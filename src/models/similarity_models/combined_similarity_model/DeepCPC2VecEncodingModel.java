package models.similarity_models.combined_similarity_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import lombok.Getter;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.api.layers.IOutputLayer;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.LearningRatePolicy;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.graph.L2NormalizeVertex;
import org.deeplearning4j.nn.conf.graph.PreprocessorVertex;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToRnnPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.RnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.graph.vertex.VertexIndices;
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
import test.ReshapeVertex;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 12/24/2017.
 */
public class DeepCPC2VecEncodingModel extends AbstractCombinedSimilarityModel<ComputationGraph,DeepCPC2VecEncodingPipelineManager> {
    public static final String VAE_NETWORK = "vaeNet";
    public static final File BASE_DIR = new File("deep_cpc_2_vec2_encoding_data"); //new File("deep_cpc_2_vec_encoding_data");

    private List<ComputationGraph> networks;
    @Getter
    private ComputationGraph vaeNetwork;

    int encodingIdx = 6;
    private int vectorSize;
    public DeepCPC2VecEncodingModel(DeepCPC2VecEncodingPipelineManager pipelineManager, String modelName, int vectorSize) {
        super(pipelineManager,ComputationGraph.class,modelName);
        this.vectorSize=vectorSize;
    }

    public int getVectorSize() {
        return vectorSize;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        final int sampleLength = 30;
        final int assigneeSamples = 30;

        Map<String,INDArray> cpc2VecMap = pipelineManager.wordCPC2VecPipelineManager.getOrLoadCPCVectors();
        CPCHierarchy cpcHierarchy = new CPCHierarchy();
        cpcHierarchy.loadGraph();

        final Random rand = new Random(32);
        final AtomicInteger incomplete = new AtomicInteger(0);
        final AtomicInteger cnt = new AtomicInteger(0);

        Map<String,INDArray> finalPredictionsMap = Collections.synchronizedMap(new HashMap<>(assets.size()+assignees.size()+classCodes.size()));

        // add cpc vectors
        cnt.set(0);
        incomplete.set(0);
        int batchSize = 5000;
        for(int i = 0; i < 1 + classCodes.size()/batchSize; i++) {
            if (i * batchSize >= classCodes.size()) continue;
            List<String> codes = classCodes.subList(i * batchSize, Math.min(classCodes.size(), i * batchSize + batchSize));
            int before = codes.size();
            codes = codes.stream().filter(cpc->cpc2VecMap.containsKey(cpc)).collect(Collectors.toList());
            int after = codes.size();
            incomplete.getAndAdd(before-after);
            if(codes.isEmpty()) continue;
            INDArray allFeatures = Nd4j.zeros(codes.size(),getVectorSize(),9);
            INDArray allMasks = Nd4j.zeros(codes.size(),9);
            AtomicInteger idx = new AtomicInteger(0);
            codes.forEach(cpc -> {
                INDArray cpc2Vec = cpc2VecMap.get(cpc);
                INDArray feature = cpc2Vec;

                // add parent cpc info
                CPC cpcObj = cpcHierarchy.getLabelToCPCMap().get(cpc);
                CPC parent = cpcObj.getParent();
                if (parent != null) {
                    INDArray parentFeature = cpc2VecMap.get(parent.getName());
                    if (parentFeature != null) {
                        CPC grandParent = parent.getParent();
                        if (grandParent != null) {
                            INDArray grandParentFeature = cpc2VecMap.get(grandParent.getName());
                            if (grandParentFeature != null) {
                                feature = Nd4j.vstack(feature, feature, parentFeature, feature, grandParentFeature, feature, parentFeature, feature, feature).reshape(cpc2Vec.length(), 9);
                            } else {
                                feature = Nd4j.vstack(feature, feature, parentFeature, feature, parentFeature, feature, feature).reshape(cpc2Vec.length(), 7);
                            }
                        } else {
                            feature = Nd4j.vstack(feature, feature, parentFeature, feature, parentFeature, feature, feature).reshape(cpc2Vec.length(), 7);
                        }
                    } else {
                        feature = Nd4j.vstack(feature, feature, feature, feature, feature).reshape(cpc2Vec.length(), 5);
                    }
                } else {
                    feature = Nd4j.vstack(feature, feature, feature, feature, feature).reshape(cpc2Vec.length(), 5);
                }

                allFeatures.get(NDArrayIndex.point(idx.get()),NDArrayIndex.all(),NDArrayIndex.interval(0,feature.shape()[1])).assign(feature);
                allMasks.get(NDArrayIndex.point(idx.get()),NDArrayIndex.interval(0,feature.shape()[1])).assign(1);


                if (cnt.get() % 50000 == 49999) {
                    System.gc();
                }
                if (cnt.getAndIncrement() % 10000 == 9999) {
                    System.out.println("Finished " + cnt.get() + " out of " + classCodes.size() + " cpcs. Incomplete: " + incomplete.get() + " / " + cnt.get());
                }
                idx.getAndIncrement();
            });


            System.out.println("All Features batch shape: "+allFeatures.shapeInfoToString());
            System.out.println("All Masks batch shape: "+allMasks.shapeInfoToString());
            INDArray encoding = encode(allFeatures, allMasks);
            System.out.println("Encoding batch shape: "+encoding.shapeInfoToString());

            idx.set(0);
            codes.forEach(code->{
                finalPredictionsMap.put(code, Transforms.unitVec(encoding.getRow(idx.get())));
            });
        }


        System.out.println("Finished class codes...");


        Map<String,List<String>> cpcMap = pipelineManager.wordCPC2VecPipelineManager.getCPCMap().entrySet().parallelStream().map(e ->new Pair<>(e.getKey(),e.getValue().stream().filter(cpc->cpc2VecMap.containsKey(cpc.getName())).limit(100).map(cpc -> cpc.getName()).collect(Collectors.toList())))
                .filter(e->e.getSecond().size()>0).collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));


        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
        Collection<String> filings = Collections.synchronizedSet(new HashSet<>());
        for(String asset : assets) {
            // get filing
            String filing = assetToFilingMap.getApplicationDataMap().getOrDefault(asset,assetToFilingMap.getPatentDataMap().get(asset));
            if(filing!=null) {
                filings.add(filing);
            }

        }

        cnt.set(0);
        incomplete.set(0);

        // add assignee vectors
        Map<String,List<String>> assigneeToCpcMap = assignees.parallelStream().collect(Collectors.toConcurrentMap(assignee->assignee,assignee->{
            return Stream.of(
                    Database.selectApplicationNumbersFromExactAssignee(assignee).stream().flatMap(asset->new AssetToCPCMap().getApplicationDataMap().getOrDefault(asset,Collections.emptySet()).stream()),
                    Database.selectPatentNumbersFromExactAssignee(assignee).stream().flatMap(asset->new AssetToCPCMap().getPatentDataMap().getOrDefault(asset,Collections.emptySet()).stream())
            ).flatMap(stream->stream).collect(Collectors.toList());
        }));

        cnt.set(0);
        incomplete.set(0);
        assignees.forEach(assignee->{
            List<String> cpcs = assigneeToCpcMap.getOrDefault(assignee, Collections.emptyList()).stream().filter(cpc->cpc2VecMap.containsKey(cpc)).collect(Collectors.toCollection(ArrayList::new));
            if(cpcs.size()>0) {
                int samples = Math.min(assigneeSamples,cpcs.size());
                INDArray assigneeFeatures = Nd4j.create(assigneeSamples,getVectorSize(),sampleLength);
                for(int i = 0; i < samples; i++) {
                    for(int j = 0; j < sampleLength; j++) {
                        INDArray cpcVector = cpc2VecMap.get(cpcs.get(rand.nextInt(cpcs.size())));
                        assigneeFeatures.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.point(j)).assign(cpcVector);
                    }
                }
                INDArray encoding = encode(assigneeFeatures,null);
                finalPredictionsMap.put(assignee, Transforms.unitVec(encoding.mean(0)));

            } else {
                incomplete.getAndIncrement();
            }

            if(cnt.get()%50000==49999) {
                System.gc();
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Finished "+cnt.get()+" out of "+assignees.size()+" assignees. Incomplete: "+incomplete.get()+" / "+cnt.get());
            }
        });


        List<String> filingsList = new ArrayList<>(filings);
        cnt.set(0);
        incomplete.set(0);
        IntStream.range(0,1+(filingsList.size()/batchSize)).forEach(i->{
            int start = i*batchSize;
            int end = Math.min(start+batchSize,filingsList.size());
            if(start<end) {
                int before = start-end;
                List<String> range = filingsList.subList(start,end).stream().filter(filing->cpcMap.containsKey(filing)).collect(Collectors.toList());
                int rangeLength = range.size();
                incomplete.getAndAdd(before-rangeLength);
                if(rangeLength>0) {
                    INDArray features = Nd4j.create(rangeLength,getVectorSize(),rangeLength);
                    List<String> featureNames = new ArrayList<>();

                    AtomicInteger idx = new AtomicInteger(0);
                    range.forEach(filing-> {
                        // word info
                        List<String> cpcNames = cpcMap.get(filing);

                        final INDArray featuresVec = Nd4j.create(getVectorSize(), sampleLength);
                        IntStream.range(0, sampleLength).forEach(h -> featuresVec.putColumn(h,cpc2VecMap.get(cpcNames.get(rand.nextInt(cpcNames.size())))));


                        features.get(NDArrayIndex.point(idx.getAndIncrement()),NDArrayIndex.all(),NDArrayIndex.all()).assign(featuresVec);
                        featureNames.add(filing);

                    });

                    INDArray encoding = encode(features,null);


                    for (int j = 0; j < encoding.rows(); j++) {
                        String label = featureNames.get(j);
                        INDArray averageEncoding = Transforms.unitVec(encoding.get(NDArrayIndex.point(j), NDArrayIndex.all()));
                        if (averageEncoding.length() != getVectorSize()) {
                            throw new RuntimeException("Wrong vector size: " + averageEncoding.length() + " != " + getVectorSize());
                        }
                        finalPredictionsMap.put(label, averageEncoding);
                        if (cnt.get() % 100000 == 99999) {
                            System.gc();
                        }
                        if (cnt.getAndIncrement() % 10000 == 9999) {
                            System.out.println("Finished " + cnt.get() + " filings out of " + filingsList.size() + ". Incomplete: " + incomplete.get() + " / " + cnt.get());
                        }
                    }

                }
            }
        });

        System.out.println("FINAL:: Finished "+cnt.get()+" filings out of "+filings.size()+". Incomplete: "+incomplete.get()+ " / "+cnt.get());

        return finalPredictionsMap;
    }

    public synchronized INDArray encode(INDArray input, INDArray mask) {
        if(vaeNetwork==null) {
            vaeNetwork = getNetworks().get(VAE_NETWORK);
        }
        if(mask==null) {
            vaeNetwork.clearLayerMaskArrays();
        } else {
            vaeNetwork.setLayerMaskArrays(new INDArray[]{mask}, new INDArray[]{mask});
        }
        INDArray res = feedForwardToVertex(vaeNetwork,String.valueOf(encodingIdx),input); // activations.get(String.valueOf(encodingIdx));
        if(res.shape().length!=2||res.columns()!=getVectorSize()) {
            vaeNetwork.getConfiguration().getVertices().forEach((k,v)->{
                System.out.println(k+": "+v.toString());
            });
            if(res.shape().length!=2) {
                throw new RuntimeException("Encoding is not a matrix. Num dims: "+res.shape().length+ " != "+2);
            }
            throw new RuntimeException("Wrong vector size: "+res.columns()+" != "+getVectorSize());
        }
        vaeNetwork.clearLayerMaskArrays();
        return res;
    }

    public static INDArray feedForwardToVertex(ComputationGraph encoder, String vertexName, INDArray... inputs) {
        for(int i = 0; i < inputs.length; i++) {
            encoder.setInput(i, inputs[i]);
        }

        boolean excludeOutputLayers = false;
        boolean train = false;
        for(int i = 0; i < encoder.topologicalSortOrder().length; ++i) {
            org.deeplearning4j.nn.graph.vertex.GraphVertex current = encoder.getVertices()[encoder.topologicalSortOrder()[i]];
            VertexIndices[] var8;
            int var9;
            int var10;
            VertexIndices v;
            int vIdx;
            int inputNum;
            if(current.isInputVertex()) {
                VertexIndices[] var14 = current.getOutputVertices();
                INDArray var15 = encoder.getInputs()[current.getVertexIndex()];
                if(current.getVertexName().equals(vertexName)) return var15;
                var8 = var14;
                var9 = var14.length;

                for(var10 = 0; var10 < var9; ++var10) {
                    v = var8[var10];
                    vIdx = v.getVertexIndex();
                    inputNum = v.getVertexEdgeNumber();
                    encoder.getVertices()[vIdx].setInput(inputNum, var15.dup());
                }
            } else if(!excludeOutputLayers || !current.isOutputVertex() || !current.hasLayer() || !(current.getLayer() instanceof IOutputLayer)) {
                INDArray out = current.doForward(train);
                if(current.hasLayer()) {
                    if(current.getVertexName().equals(vertexName)) return out;
                }

                VertexIndices[] outputsTo = current.getOutputVertices();
                if(outputsTo != null) {
                    var8 = outputsTo;
                    var9 = outputsTo.length;

                    for(var10 = 0; var10 < var9; ++var10) {
                        v = var8[var10];
                        vIdx = v.getVertexIndex();
                        inputNum = v.getVertexEdgeNumber();
                        encoder.getVertices()[vIdx].setInput(inputNum, out);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public int printIterations() {
        return 2000;
    }


    @Override
    protected Map<String, ComputationGraph> buildNetworksForTraining() {
        Map<String, ComputationGraph> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());

        System.out.println("Build model....");


        networks = new ArrayList<>();

        // build networks
        double learningRate = 0.05;
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
                    System.out.println("Shape of " + j + ": " + Arrays.toString(DeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(j), data3).shape()));
                    System.out.println("Shape of " + j + ": " + Arrays.toString(DeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(j), data5).shape()));
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
        double newLearningRate = 0.01;
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
                score+=test(vaeNetwork,dataSet);
                count++;
                //System.gc();
            }
            validationIterator.reset();

            return score/count;
        };
    }

    private ComputationGraphConfiguration.GraphBuilder createNetworkConf(double learningRate) {
        int hiddenLayerSizeRNN = 48;
        int maxSamples = pipelineManager.getMaxSamples();
        int linearTotal = hiddenLayerSizeRNN * maxSamples;
        int hiddenLayerSizeFF = 48;
        int input1 = WordCPC2VecPipelineManager.modelNameToVectorSizeMap.get(WordCPC2VecPipelineManager.DEEP_MODEL_NAME);


        Updater updater = Updater.RMSPROP;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        Activation activation = Activation.TANH;
        Activation outputActivation = Activation.TANH;
        return new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(learningRate)
               // .lrPolicyDecayRate(0.0001)
               // .lrPolicyPower(0.7)
               // .learningRateDecayPolicy(LearningRatePolicy.Inverse)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .activation(activation)
                .graphBuilder()
                .addInputs("x1")
                .addVertex("0", new L2NormalizeVertex(), "x1")
                .addLayer("1", new GravesBidirectionalLSTM.Builder().nIn(input1).nOut(hiddenLayerSizeRNN).build(), "0")
                .addLayer("2", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "1")
                .addLayer("3", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "2")
                //.addVertex("v1", new PreprocessorVertex(new RnnToFeedForwardPreProcessor()), "3")
                .addVertex("v2", new ReshapeVertex(-1,linearTotal), "3")
                .addLayer("4", new DenseLayer.Builder().nIn(linearTotal).nOut(linearTotal).build(), "v2")
                .addLayer("5", new DenseLayer.Builder().nIn(linearTotal).nOut(hiddenLayerSizeFF).build(), "4")
                .addLayer("6", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(vectorSize).build(), "5")
                .addLayer("7", new DenseLayer.Builder().nIn(vectorSize).nOut(hiddenLayerSizeFF).build(), "6")
                .addLayer("8", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(linearTotal).build(), "7")
                .addLayer("9", new DenseLayer.Builder().nIn(linearTotal).nOut(linearTotal).build(), "8")
                .addVertex("v3", new ReshapeVertex(-1,hiddenLayerSizeRNN,maxSamples), "9")
                //.addVertex("v4", new PreprocessorVertex(new FeedForwardToRnnPreProcessor()), "v3")
                .addLayer("10", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "v3")
                .addLayer("11", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "10")
                .addLayer("12", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "11")
                .addLayer("y1", new RnnOutputLayer.Builder().activation(outputActivation).nIn(hiddenLayerSizeRNN).lossFunction(lossFunction).nOut(input1).build(), "12")
                .setOutputs("y1")
                .backprop(true)
                .pretrain(false);
    }

    public static double test(ComputationGraph net, MultiDataSet finalDataSet) {
        //System.out.println("ds shape: "+Arrays.toString(finalDataSet.getLabels()[0].shape()));
        double score = net.score(finalDataSet,false);
        return 1d + score/finalDataSet.getFeatures(0).shape()[2];
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
                        vaeNetwork.fit(ds);
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
