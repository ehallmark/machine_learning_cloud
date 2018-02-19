package models.similarity_models.combined_similarity_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import lombok.Getter;
import models.NDArrayHelper;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.LearningRatePolicy;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.graph.L2NormalizeVertex;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;
import tools.ReshapeVertex;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedDeepCPC2VecEncodingModel extends AbstractEncodingModel<ComputationGraph,CombinedDeepCPC2VecEncodingPipelineManager> {
    public static final String VAE_NETWORK = "vaeNet";
    public static final File BASE_DIR = new File("combined_deep_cpc_2_vec_encoding_data"); //new File("deep_cpc_2_vec_encoding_data");

    private List<ComputationGraph> networks;
    @Getter
    private ComputationGraph vaeNetwork;

    String encodingLabel = "v";
    private int vectorSize;
    public CombinedDeepCPC2VecEncodingModel(CombinedDeepCPC2VecEncodingPipelineManager pipelineManager, String modelName, int vectorSize) {
        super(pipelineManager,ComputationGraph.class,modelName);
        this.vectorSize=vectorSize;
    }

    public int getVectorSize() {
        return vectorSize;
    }

    private Map<String,Double[]> createVectorMap(List<String> filings, INDArray encoding) {
        Map<String,Double[]> map = new HashMap<>();
        double[] data = Nd4j.toFlattened('c',encoding).data().asDouble();
        if(data.length!=filings.size()*vectorSize) {
            throw new RuntimeException("Invalid lengths: Data length="+data.length+" != "+vectorSize*filings.size());
        }
        for(int i = 0; i < filings.size(); i++) {
            map.put(filings.get(i),toObj(Arrays.copyOfRange(data,i*vectorSize,i*vectorSize+vectorSize)));
        }
        return map;
    }

    private static Double[] toObj(double[] in) {
        Double[] d = new Double[in.length];
        for(int i = 0; i < d.length; i++) {
            d[i]=in[i];
        }
        return d;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        final int sampleLength = pipelineManager.getMaxSamples();
        final int assigneeSamples = 32;
        final int patentSamples = 6;
        ForkJoinPool pool = new ForkJoinPool(2);

        Set<String> alreadyPredicted;
        try {
            alreadyPredicted = Collections.synchronizedSet(new HashSet<>(Database.loadAllFilingsWithVectors()));
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to read filings...");
        }
        assignees = assignees.parallelStream().filter(a->!alreadyPredicted.contains(a)).collect(Collectors.toList());
        classCodes = classCodes.parallelStream().filter(a->!alreadyPredicted.contains(a)).collect(Collectors.toList());

        final boolean encodeAssets = true;
        final boolean encodeAssignees = true;
        final boolean encodeClassCodes = true;

        Word2Vec word2Vec = pipelineManager.getWord2Vec();
        if(word2Vec==null) throw new RuntimeException("Cannot run predictions without setting word2vec model.");
        CPCHierarchy cpcHierarchy = new CPCHierarchy();
        cpcHierarchy.loadGraph();

        final Random rand = new Random(32);
        final AtomicInteger incomplete = new AtomicInteger(0);
        final AtomicInteger cnt = new AtomicInteger(0);

        //Map<String, INDArray> finalPredictionsMap = Collections.synchronizedMap(new HashMap<>(assets.size() + assignees.size() + classCodes.size()));

        if(classCodes.size()>0&&encodeClassCodes) {
            // add cpc vectors
            cnt.set(0);
            incomplete.set(0);
            int batchSize = 5000;
            List<List<String>> cpcBatches = createBatches(classCodes,batchSize);
            for(List<String> codes : cpcBatches) {
                int before = codes.size();
                List<String> wordVectorCodes = new ArrayList<>(codes.size());
                codes = codes.stream().filter(cpc->{
                    // default to parent if neccessary
                    if(word2Vec.hasWord(cpc)) {
                        wordVectorCodes.add(cpc);
                        return true;
                    } else {
                        CPC c = cpcHierarchy.getLabelToCPCMap().get(cpc);
                        if(c!=null&&c.getParent()!=null&&word2Vec.hasWord(c.getParent().getName())) {
                            wordVectorCodes.add(c.getParent().getName());
                            return true;
                        }
                    }
                    return false;
                }).collect(Collectors.toList());
                if (codes.isEmpty()) continue;
                INDArray vaeVec = ((DeepCPCVariationalAutoEncoderNN) pipelineManager.deepCPCVAEPipelineManager.getModel()).encodeCPCs(codes);
                if(vaeVec==null) continue;

                int after = codes.size();
                incomplete.getAndAdd(before - after);
                INDArray allFeatures = Nd4j.zeros(codes.size(), getVectorSize(), pipelineManager.getMaxSamples());
                AtomicInteger idx = new AtomicInteger(0);
                INDArray codeVecs = word2Vec.getWordVectors(wordVectorCodes);
                for (int cpcIdx = 0; cpcIdx < codes.size(); cpcIdx++) {
                    INDArray feature = codeVecs.getRow(cpcIdx);
                    String cpcCode = wordVectorCodes.get(cpcIdx);

                    // add parent cpc info
                    CPC cpcObj = cpcHierarchy.getLabelToCPCMap().get(cpcCode);
                    CPC parent = cpcObj.getParent();
                    if (parent != null) {
                        INDArray parentFeature = word2Vec.getLookupTable().vector(parent.getName());
                        if (parentFeature != null) {
                            CPC grandParent = parent.getParent();
                            if (grandParent != null) {
                                INDArray grandParentFeature = word2Vec.getLookupTable().vector(grandParent.getName());
                                if (grandParentFeature != null) {
                                    feature = Nd4j.vstack(feature, parentFeature, feature, grandParentFeature, parentFeature, feature);
                                } else {
                                    feature = Nd4j.vstack(feature, parentFeature, feature, feature, parentFeature, feature);
                                }
                            } else {
                                feature = Nd4j.vstack(feature, parentFeature, feature, feature, parentFeature, feature);
                            }
                        } else {
                            feature = Nd4j.vstack(feature, feature, feature, feature, feature, feature);
                        }
                    } else {
                        feature = Nd4j.vstack(feature, feature, feature, feature, feature, feature);
                    }

                    allFeatures.get(NDArrayIndex.point(idx.get()), NDArrayIndex.all(), NDArrayIndex.all()).assign(feature.transpose());


                    if (cnt.get() % 5000 == 4999) {
                        System.gc();
                    }
                    if (cnt.getAndIncrement() % 10000 == 9999) {
                        System.out.println("Finished " + cnt.get() + " out of " + classCodes.size() + " cpcs. Incomplete: " + incomplete.get() + " / " + cnt.get());
                    }
                    idx.getAndIncrement();
                }


                System.out.println("All Features batch shape: " + allFeatures.shapeInfoToString());
                INDArray encoding = encode(allFeatures, vaeVec);
                encoding.diviColumnVector(encoding.norm2(1));
                System.out.println("Encoding batch shape: " + encoding.shapeInfoToString());

                idx.set(0);
                try {
                    pool.execute(Database.upsertVectors(createVectorMap(codes, encoding)));
                } catch(Exception e) {
                    e.printStackTrace();
                    System.out.println("Error upserting to postgres...");
                    System.exit(1);
                }
            }


            System.out.println("Finished class codes...");
        }



        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
        Collection<String> filings = Collections.synchronizedSet(new HashSet<>());
        for (String asset : assets) {
            // get filing
            String filing = assetToFilingMap.getApplicationDataMap().getOrDefault(asset, assetToFilingMap.getPatentDataMap().get(asset));
            if (filing != null) {
                filings.add(filing);
            }

        }


        if(assignees.size()>0&&encodeAssignees) {
            cnt.set(0);
            incomplete.set(0);

            // add assignee vectors
            Map<String, List<String>> assigneeToCpcMap = assignees.parallelStream().collect(Collectors.toConcurrentMap(assignee -> assignee, assignee -> {
                return Stream.of(
                        Database.selectApplicationNumbersFromExactAssignee(assignee).stream().flatMap(asset -> new AssetToCPCMap().getApplicationDataMap().getOrDefault(asset, Collections.emptySet()).stream()),
                        Database.selectPatentNumbersFromExactAssignee(assignee).stream().flatMap(asset -> new AssetToCPCMap().getPatentDataMap().getOrDefault(asset, Collections.emptySet()).stream())
                ).flatMap(stream -> stream).filter(word2Vec::hasWord).collect(Collectors.toList());
            }));

            cnt.set(0);
            incomplete.set(0);

            int batchSize = 1000;

            final int numAssigneesBeforeFilter = assignees.size();

            assignees = assignees.stream().filter(assignee->assigneeToCpcMap.containsKey(assignee)&&assigneeToCpcMap.get(assignee).size()>0).collect(Collectors.toList());

            final int numAssignees = assignees.size();

            incomplete.set(numAssigneesBeforeFilter-numAssignees);

            List<List<String>> assigneeBatches = createBatches(assignees, batchSize);
            assigneeBatches.forEach(assigneeBatch -> {
                if(assigneeBatch.isEmpty()) return;

                INDArray allFeatures1 = Nd4j.create(assigneeSamples*assigneeBatch.size(),getVectorSize(),sampleLength);
                INDArray allFeatures2 = Nd4j.create(assigneeSamples*assigneeBatch.size(),getVectorSize());
                List<List<String>> itemsGrouped = new ArrayList<>(assigneeSamples);
                for(int b = 0; b < assigneeBatch.size(); b++) {
                    String assignee = assigneeBatch.get(b);
                    List<String> cpcs = assigneeToCpcMap.getOrDefault(assignee, Collections.emptyList());
                    List<String> items = IntStream.range(0, sampleLength * assigneeSamples).mapToObj(k -> cpcs.get(rand.nextInt(cpcs.size()))).collect(Collectors.toList());
                    INDArray cpcVector = word2Vec.getWordVectors(items);
                    for (int i = 0; i < assigneeSamples; i++) {
                        allFeatures1.get(NDArrayIndex.point(b*assigneeSamples+i), NDArrayIndex.all(), NDArrayIndex.all()).assign(cpcVector.get(NDArrayIndex.interval(i * sampleLength, i * sampleLength + sampleLength), NDArrayIndex.all()).transpose());
                    }
                    itemsGrouped.add(items);
                }
                INDArray vaeVec = ((DeepCPCVariationalAutoEncoderNN) pipelineManager.deepCPCVAEPipelineManager.getModel()).encodeCPCsMultiple(itemsGrouped);
                //  System.out.println("F1: "+Arrays.toString(assigneeFeatures.shape()));
                //  System.out.println("F2: "+Arrays.toString(vaeVec.shape()));
                for(int i = 0; i < assigneeBatch.size(); i++) {
                    allFeatures2.get(NDArrayIndex.interval(i*assigneeSamples,i*assigneeSamples+assigneeSamples),NDArrayIndex.all())
                            .assign(vaeVec.getRow(i).broadcast(assigneeSamples,getVectorSize()));
                }
                INDArray preCoding = encode(allFeatures1, allFeatures2);
                INDArray encoding = Nd4j.create(assigneeBatch.size(),getVectorSize());
                for(int b = 0; b < assigneeBatch.size(); b++) {
                    encoding.putRow(b, preCoding.get(NDArrayIndex.interval(assigneeSamples*b,assigneeSamples*b+assigneeSamples)).mean(0));

                    if(cnt.get() % 1000==999) {
                        System.out.print("-");
                    }
                    if (cnt.get() % 5000 == 4999) {
                        System.gc();
                    }
                    if (cnt.getAndIncrement() % 10000 == 9999) {
                        System.out.println("Finished " + cnt.get() + " out of " + numAssignees + " assignees. Incomplete: " + incomplete.get() + " / " + cnt.get());
                    }
                }
                encoding.diviColumnVector(encoding.norm2(1));
                try {
                    pool.execute(Database.upsertVectors(createVectorMap(assigneeBatch, encoding)));
                } catch(Exception e) {
                    e.printStackTrace();
                    System.out.println("Error upserting to postgres...");
                    System.exit(1);
                }
            });
        }

        if(filings.size()>0&&encodeAssets) {
            Map<String, List<String>> filingToCPCMap = pipelineManager.wordCPC2VecPipelineManager.getCPCMap()
                    .entrySet().parallelStream().map(e->new Pair<>(e.getKey(),e.getValue().stream().filter(cpc->word2Vec.hasWord(cpc.getName())).flatMap(w->IntStream.range(0,w.getNumParts()).mapToObj(i->w.getName())).collect(Collectors.toList())))
                    .filter(p->p.getSecond().size()>0)
                    .collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));
            filings = filings.parallelStream().filter(a->!alreadyPredicted.contains(a)).collect(Collectors.toList());
            int preSize = filings.size();
            filings = filings.stream().filter(filing->filingToCPCMap.containsKey(filing)&&filingToCPCMap.get(filing).size()>0).collect(Collectors.toList());
            List<String> filingsList = new ArrayList<>(filings);
            cnt.set(0);
            incomplete.set(preSize-filings.size());

            int batchSize = 40000;
            int gc = 20000;

            List<List<String>> filingBatches = createBatches(filingsList, batchSize);
            filingBatches.forEach(filingBatch -> {
                if(filingBatch.isEmpty()) return;
                long t0 = System.currentTimeMillis();

                INDArray allFeatures1 = Nd4j.create(patentSamples * filingBatch.size(), getVectorSize(), sampleLength);
                INDArray allFeatures2 = Nd4j.create(patentSamples * filingBatch.size(), getVectorSize());
                List<List<String>> itemsGrouped = new ArrayList<>(patentSamples);

                for(int b = 0; b < filingBatch.size(); b++) {
                    String filing = filingBatch.get(b);
                    List<String> cpcs = filingToCPCMap.getOrDefault(filing, Collections.emptyList());
                    List<String> distinctCpcs = new ArrayList<>(new HashSet<>(cpcs));
                    List<String> items = IntStream.range(0, sampleLength * patentSamples).mapToObj(k -> cpcs.get(rand.nextInt(cpcs.size()))).collect(Collectors.toList());
                    INDArray cpcVector = word2Vec.getWordVectors(items);
                    for (int i = 0; i < patentSamples; i++) {
                        allFeatures1.get(NDArrayIndex.point(b*patentSamples+i), NDArrayIndex.all(), NDArrayIndex.all()).assign(cpcVector.get(NDArrayIndex.interval(i * sampleLength, i * sampleLength + sampleLength), NDArrayIndex.all()).transpose());
                    }
                    itemsGrouped.add(distinctCpcs);
                }
                INDArray vaeVec = ((DeepCPCVariationalAutoEncoderNN) pipelineManager.deepCPCVAEPipelineManager.getModel()).encodeCPCsMultiple(itemsGrouped);
                //  System.out.println("F1: "+Arrays.toString(assigneeFeatures.shape()));
                //  System.out.println("F2: "+Arrays.toString(vaeVec.shape()));
                for(int i = 0; i < filingBatch.size(); i++) {
                    allFeatures2.get(NDArrayIndex.interval(i*patentSamples,i*patentSamples+patentSamples),NDArrayIndex.all())
                            .assign(vaeVec.getRow(i).broadcast(patentSamples,getVectorSize()));
                }
                INDArray preCoding = encode(allFeatures1, allFeatures2);
                INDArray encoding = Nd4j.create(filingBatch.size(),vectorSize);
                for(int b = 0; b < filingBatch.size(); b++) {
                    encoding.putRow(b, preCoding.get(NDArrayIndex.interval(patentSamples*b,patentSamples*b+patentSamples)).mean(0));

                    if(cnt.get() % 1000==999) {
                        System.out.print("-");
                    }
                    if (cnt.get() % gc == gc-1) {
                        //System.out.println("Garbage collecting...");
                        System.gc();
                    }
                    if (cnt.getAndIncrement() % 10000 == 9999) {
                        System.out.println("Finished " + cnt.get() + " out of " + filingsList.size() + " filings. "+ " Incomplete: " + incomplete.get() + " / " + cnt.get());
                    }
                }

                encoding.diviColumnVector(encoding.norm2(1));
                try {
                    pool.execute(Database.upsertVectors(createVectorMap(filingBatch, encoding)));
                } catch(Exception e) {
                    e.printStackTrace();
                    System.out.println("Error upserting to postgres...");
                    System.exit(1);
                }
                long t1 = System.currentTimeMillis();
                double t = t1-t0;
                t /= (60*1000);
                System.out.println("Time to complete batch: "+t+" minutes.");
            });

        }

        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("While shutting down pool...");
        }
        System.out.println("FINAL:: Finished "+cnt.get()+" filings out of "+filings.size()+". Incomplete: "+incomplete.get()+ " / "+cnt.get());

        Map<String,INDArray> predictions;
        try {
            System.out.println("Garbage collecting...");
            System.gc(); System.gc(); System.gc();
            System.out.println("Finished GC.");
            predictions = Database.loadVectorPredictions();
            Map<String,INDArray> cpcVectors = Collections.synchronizedMap(new HashMap<>(classCodes.size()));
            predictions.entrySet().parallelStream().forEach(e->{
                if(cpcHierarchy.getLabelToCPCMap().containsKey(e.getKey())) {
                    cpcVectors.put(e.getKey(),e.getValue());
                }
            });
            System.out.println("Num cpc vectors: "+cpcVectors.size());
            Database.trySaveObject(cpcVectors,CombinedDeepCPC2VecEncodingPipelineManager.cpcVectorsFile);
        } catch(Exception e) {
            predictions = null;
            e.printStackTrace();
            System.out.println("While loading vectors from postgres...");
        }
        return predictions;
    }

    public static List<List<String>> createBatches(List<String> in, int batchSize) {
        return IntStream.range(0,1+in.size()/batchSize).mapToObj(i->{
            if (i * batchSize >= in.size()) return null;
            return in.subList(i*batchSize,Math.min(i*batchSize+batchSize,in.size()));
        }).filter(l->l!=null&&l.size()>0).collect(Collectors.toList());
    }


    public synchronized INDArray encode(String patent, List<String> words, int samples) {
        DeepCPCVariationalAutoEncoderNN vae = (DeepCPCVariationalAutoEncoderNN)pipelineManager.deepCPCVAEPipelineManager.getModel();
        INDArray vaeVec = vae.encode(Collections.singletonList(patent));
        if(vaeVec==null) return null;
        vaeVec = vaeVec.broadcast(samples,vaeVec.length());
        if(pipelineManager.getWord2Vec()==null) throw new NullPointerException("Must set word2vec before encoding.");
        INDArray wordVec = sampleWordVectors(words,samples,pipelineManager.getMaxSamples(),pipelineManager.getWord2Vec());
        if(wordVec==null) return null;
        INDArray encoding = encode(wordVec,vaeVec);
        if(encoding==null) return null;
        return encoding.mean(0);
    }

    public synchronized INDArray encode(INDArray input1, INDArray input2) {
        if(vaeNetwork==null) {
            vaeNetwork = getNetworks().get(VAE_NETWORK);
        }
        INDArray res = feedForwardToVertex(vaeNetwork,encodingLabel,input1,input2); // activations.get(String.valueOf(encodingIdx));
        if(res.shape().length!=2||res.columns()!=getVectorSize()) {
            vaeNetwork.getConfiguration().getVertices().forEach((k,v)->{
                System.out.println(k+": "+v.toString());
            });
            if(res.shape().length!=2) {
                throw new RuntimeException("Encoding is not a matrix. Num dims: "+res.shape().length+ " != "+2);
            }
            throw new RuntimeException("Wrong vector size: "+res.columns()+" != "+getVectorSize());
        }
        return res;
    }

    // do not use for encoding patents
    public synchronized INDArray encodeText(List<String> text, int samples) {
        CombinedCPC2Vec2VAEEncodingPipelineManager manager = CombinedCPC2Vec2VAEEncodingPipelineManager.getOrLoadManager(true);
        CombinedCPC2Vec2VAEEncodingModel model = (CombinedCPC2Vec2VAEEncodingModel)manager.getModel();
        Word2Vec word2Vec = pipelineManager.getWord2Vec();
        if(word2Vec==null) throw new RuntimeException("Word2Vec cannot be null during encoding");
        text = text.stream().filter(word->word2Vec.hasWord(word)).collect(Collectors.toList());
        if(text.isEmpty()) return null;
        INDArray inputs = sampleWordVectors(text,samples,pipelineManager.getMaxSamples(),pipelineManager.getWord2Vec());
        if(inputs==null) return null;
        INDArray vaeVec = model.encodeText(inputs);
        if(vaeVec==null) return null;
        INDArray encoding = encode(inputs,vaeVec);
        if(encoding==null) return null;
        return Transforms.unitVec(encoding.mean(0));
    }


    @Override
    public int printIterations() {
        return 200;
    }


    @Override
    protected Map<String, ComputationGraph> buildNetworksForTraining() {
        Map<String, ComputationGraph> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());

        System.out.println("Build model....");


        networks = new ArrayList<>();

        // build networks
        double learningRate = 0.01;
        ComputationGraphConfiguration.GraphBuilder conf = createNetworkConf(learningRate);

        vaeNetwork = new ComputationGraph(conf.build());
        vaeNetwork.init();



        System.out.println("Conf: " + conf.toString());

        nameToNetworkMap.put(VAE_NETWORK, vaeNetwork);

        networks.add(vaeNetwork);
        System.out.println("Initial test: " +getTestFunction().apply(vaeNetwork));


        boolean testNet = false;
        if(testNet) {
            int input1 = WordCPC2VecPipelineManager.modelNameToVectorSizeMap.get(WordCPC2VecPipelineManager.DEEP_MODEL_NAME);
            ComputationGraph graph = new ComputationGraph(conf.build());
            graph.init();

            INDArray data3 = Nd4j.randn(new int[]{3, input1, pipelineManager.getMaxSamples()});
            INDArray data5 = Nd4j.randn(new int[]{5, input1, pipelineManager.getMaxSamples()});


            for (int j = 1; j < 9; j++) {
                try {
                    System.out.println("Shape of " + j + ": " + Arrays.toString(CombinedDeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(j), data3).shape()));
                    System.out.println("Shape of " + j + ": " + Arrays.toString(CombinedDeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(j), data5).shape()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            for (int j = 0; j < 10; j++) {
                graph.fit(new INDArray[]{data3}, new INDArray[]{data3});
                graph.fit(new INDArray[]{data5}, new INDArray[]{data5});
                System.out.println("Score " + j + ": " + graph.score());
            }

        }


        return nameToNetworkMap;
    }


    @Override
    protected Map<String, ComputationGraph> updateNetworksBeforeTraining(Map<String, ComputationGraph> networkMap) {
        // recreate net
        double newLearningRate = 0.000002;
        vaeNetwork = net.getNameToNetworkMap().get(VAE_NETWORK);
        INDArray params = vaeNetwork.params();
        vaeNetwork = new ComputationGraph(createNetworkConf(newLearningRate).build());
        vaeNetwork.init(params,false);

        // add to maps
        net.getNameToNetworkMap().put(VAE_NETWORK,vaeNetwork);
        Map<String,ComputationGraph> updates = Collections.synchronizedMap(new HashMap<>());
        updates.put(VAE_NETWORK,vaeNetwork);
        networks = new ArrayList<>();
        networks.add(vaeNetwork);
        System.out.println("Initial test: " +getTestFunction().apply(vaeNetwork));
        return updates;
    }

    @Override
    protected Function<Object, Double> getTestFunction() {
        return (v) -> {
            System.gc();
            MultiDataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
            List<MultiDataSet> validationDataSets = Collections.synchronizedList(new ArrayList<>());

            int valCount = 0;
            double score = 0d;
            int count = 0;
            while(validationIterator.hasNext()&&valCount<50000) {
                MultiDataSet dataSet = validationIterator.next();
                validationDataSets.add(dataSet);
                valCount+=dataSet.getFeatures()[0].shape()[0];
                try {
                    score += test(vaeNetwork, dataSet);
                } catch(Exception e) {
                    e.printStackTrace();
                    System.out.println("During testing...");
                }
                count++;
                //System.gc();
            }
            validationIterator.reset();

            return score/count;
        };
    }

    public static double test(ComputationGraph net, MultiDataSet finalDataSet) {
        INDArray[] inputs = finalDataSet.getFeatures();
        INDArray[] predictions = net.output(false,finalDataSet.getFeatures());
        double cosineSim = 0d;
        for(int i = 0; i < inputs.length; i++) {
            int[] shape = new int[]{inputs[i].shape()[0],0};
            shape[1] = (inputs[i].shape().length==2 ? inputs[i].shape()[1] : (inputs[i].shape()[1]*inputs[i].shape()[2]));
            cosineSim += NDArrayHelper.sumOfCosineSimByRow(inputs[i].reshape(shape),predictions[i].reshape(shape))/shape[0];
        }
        return 1 - (cosineSim/inputs.length);
    }

    private ComputationGraphConfiguration.GraphBuilder createNetworkConf(double learningRate) {
        int hiddenLayerSizeRNN = 64;
        int maxSamples = pipelineManager.getMaxSamples();
        int linearTotal = hiddenLayerSizeRNN * maxSamples;
        int hiddenLayerSizeFF = 128;
        int input1 = WordCPC2VecPipelineManager.modelNameToVectorSizeMap.get(WordCPC2VecPipelineManager.DEEP_MODEL_NAME);
        int input2 = DeepCPCVariationalAutoEncoderNN.VECTOR_SIZE;

        Updater updater = Updater.ADAM;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        Activation activation = Activation.TANH;
        Activation outputActivation = Activation.TANH;
        Map<Integer,Double> learningRateSchedule = new HashMap<>();
        learningRateSchedule.put(0,learningRate);
        //learningRateSchedule.put(20000,learningRate/5);
        //learningRateSchedule.put(50000,learningRate/5);
        //learningRateSchedule.put(200000,learningRate/25);
        //learningRateSchedule.put(300000,learningRate/55);
        return new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(learningRate)
                .learningRateDecayPolicy(LearningRatePolicy.Schedule)
                .learningRateSchedule(learningRateSchedule)
                //.regularization(true).l2(0.0001)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .activation(activation)
                .graphBuilder()
                .addInputs("x1","x2")
                .addVertex("1-0", new L2NormalizeVertex(), "x1")
                .addVertex("1-1", new L2NormalizeVertex(), "x2")
                .addLayer("2-0", new GravesBidirectionalLSTM.Builder().nIn(input1).nOut(hiddenLayerSizeRNN).build(), "1-0")
                .addLayer("2-1", new DenseLayer.Builder().nIn(input2).nOut(hiddenLayerSizeRNN).build(), "1-1")
                .addLayer("3-0", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "2-0")
                .addLayer("3-1", new DenseLayer.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "2-1")
                .addVertex("v2", new ReshapeVertex(-1,linearTotal), "3-0")
              //  .addLayer("bn1", new BatchNormalization.Builder().nIn(linearTotal+hiddenLayerSizeRNN).nOut(linearTotal+hiddenLayerSizeRNN).build(), "v2","3-1")
                .addLayer("4", new DenseLayer.Builder().nIn(linearTotal+hiddenLayerSizeRNN).nOut(linearTotal).build(), "v2","3-1")
                .addLayer("5", new DenseLayer.Builder().nIn(linearTotal).nOut(hiddenLayerSizeFF).build(), "4")
                .addLayer("6", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(hiddenLayerSizeFF).build(), "5")
                .addLayer("7", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(hiddenLayerSizeFF).build(), "6")
                .addLayer("v", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(vectorSize).build(), "7")
                .addLayer("8", new DenseLayer.Builder().nIn(vectorSize).nOut(hiddenLayerSizeFF).build(), "v")
                .addLayer("9", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(hiddenLayerSizeFF).build(), "8")
                .addLayer("10", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(hiddenLayerSizeFF).build(), "9")
                .addLayer("11", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(linearTotal).build(), "10")
                .addLayer("12", new DenseLayer.Builder().nIn(linearTotal).nOut(linearTotal).build(), "11")
              //  .addLayer("bn2", new BatchNormalization.Builder().nIn(linearTotal).nOut(linearTotal).build(), "12")
                .addVertex("v3", new ReshapeVertex(-1,hiddenLayerSizeRNN,maxSamples), "12")
                .addLayer("13-0", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "v3")
                .addLayer("13-1", new DenseLayer.Builder().nIn(linearTotal).nOut(hiddenLayerSizeRNN).build(), "12")
                .addLayer("14-0", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "13-0")
                .addLayer("14-1", new DenseLayer.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "13-1")
                .addLayer("y1", new RnnOutputLayer.Builder().activation(outputActivation).nIn(hiddenLayerSizeRNN).lossFunction(lossFunction).nOut(input1).build(), "14-0")
                .addLayer("y2", new OutputLayer.Builder().activation(outputActivation).nIn(hiddenLayerSizeRNN).lossFunction(lossFunction).nOut(input2).build(), "14-1")
                .setOutputs("y1","y2")
                .backprop(true)
                .pretrain(false);
    }


    @Override
    protected void train(MultiDataSet dataSet) {
        throw new RuntimeException("Please use other train method.");
    }

    @Override
    protected void train(MultiDataSetIterator dataSetIterator, int nEpochs, AtomicBoolean stoppingCondition) {
        try {
            for (int i = 0; i < nEpochs; i++) {
                while(dataSetIterator.hasNext()) {
                    MultiDataSet ds = dataSetIterator.next();
                    networks.forEach(vaeNetwork->{
                        try {
                            vaeNetwork.fit(ds);
                        } catch(Exception e) {
                            e.printStackTrace();
                            System.out.println("Error occurred during network.fit();");
                        }
                    });
                }

                if (stoppingCondition.get()) break;
                dataSetIterator.reset();
            }
            if (stoppingCondition.get()) {
                System.out.println("Stopping condition reached...");
            }

        } catch(StoppingConditionMetException e) {
            System.out.println("Stopping condition met: "+e.getMessage());
        }
    }

    @Override
    protected Function<IterationListener, Void> setListenerFunction() {
        return listener -> {
            networks.forEach(network->network.setListeners(listener));
            return null;
        };
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
