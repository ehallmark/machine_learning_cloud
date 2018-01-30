package models.similarity_models.combined_similarity_model;

import data_pipeline.optimize.nn_optimization.CGRefactorer;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import lombok.Getter;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.nn.api.layers.IOutputLayer;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
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
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedVariationalAutoencoder extends AbstractCombinedSimilarityModel<ComputationGraph> {
    public static final String VAE_NETWORK = "vaeNet";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER + "combined_similarity_vae_data");

    @Getter
    private ComputationGraph vaeNetwork;
    int encodingIdx = 22;

    private int vectorSize;
    public CombinedVariationalAutoencoder(CombinedSimilarityVAEPipelineManager pipelineManager, String modelName, int vectorSize) {
        super(pipelineManager,ComputationGraph.class,modelName);
        this.vectorSize=vectorSize;
    }

    public int getVectorSize() {
        return vectorSize;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        final int numSamples = 8;
        final int sampleLength = 8;
        final int assigneeSamples = 128;
        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
        Collection<String> filings = Collections.synchronizedSet(new HashSet<>());
        for(String asset : assets) {
            // get filing
            String filing = assetToFilingMap.getApplicationDataMap().getOrDefault(asset,assetToFilingMap.getPatentDataMap().get(asset));
            if(filing!=null) {
                filings.add(filing);
            }
        };


        Map<String,INDArray> filingCpcVaeEncoderPredictions = pipelineManager.getAssetToEncodingMap();
        Map<String,INDArray> cpc2VecMap = pipelineManager.wordCPC2VecPipelineManager.getOrLoadCPCVectors();
        Map<String,List<String>> cpcMap = pipelineManager.wordCPC2VecPipelineManager.getCPCMap().entrySet().parallelStream().map(e ->new Pair<>(e.getKey(),e.getValue().stream().filter(cpc->cpc.getNumParts() > 3 && cpc2VecMap.containsKey(cpc.getName())).limit(100).map(cpc -> cpc.getName()).collect(Collectors.toList())))
                .collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));

        final Random rand = new Random(32);

        Map<String,INDArray> finalPredictionsMap = Collections.synchronizedMap(new HashMap<>(filings.size()));

        AtomicInteger incomplete = new AtomicInteger(0);
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger nullVae = new AtomicInteger(0);
        final List<String> filingsWithVecs = filings.parallelStream().filter(filing->filingCpcVaeEncoderPredictions.containsKey(filing)).collect(Collectors.toList());
        int batchSize = 1000;
        final INDArray sampleVec = Nd4j.create(sampleLength,32);
        INDArray features = Nd4j.create(sampleLength*batchSize,64);
        IntStream.range(0,1+(filingsWithVecs.size()/batchSize)).forEach(i->{
            int start = i*batchSize;
            int end = Math.min(start+batchSize,filingsWithVecs.size());
            if(start<end) {
                List<String> range = filingsWithVecs.subList(start,end);
                List<INDArray> allFeatures = new ArrayList<>();
                List<String> featureNames = new ArrayList<>();

                range.forEach(filing-> {
                    INDArray cpcVaeVec = filingCpcVaeEncoderPredictions.get(filing);

                    // word info
                    List<String> cpcNames = cpcMap.getOrDefault(filing,Collections.emptyList());
                    if (cpcNames.isEmpty()) {
                        incomplete.getAndIncrement();
                    } else {
                        final INDArray featuresVec = Nd4j.create(numSamples, 64);
                        for (int j = 0; j < numSamples; j++) {
                            IntStream.range(0, sampleLength).forEach(h -> sampleVec.putRow(h,cpc2VecMap.get(cpcNames.get(rand.nextInt(cpcNames.size())))));
                            INDArray cpc2Vec = sampleVec.mean(0);
                            featuresVec.putRow(j, Nd4j.hstack(cpc2Vec, cpcVaeVec));
                        }

                        allFeatures.add(featuresVec);
                        featureNames.add(filing);
                    }
                });

                if(allFeatures.size()>0) {
                    for(int j = 0; j < allFeatures.size(); j++) {
                        features.get(NDArrayIndex.interval(j*numSamples,j*numSamples+numSamples),NDArrayIndex.all()).assign(allFeatures.get(j));
                    }
                    INDArray featuresView = features.get(NDArrayIndex.interval(0,allFeatures.size()*numSamples),NDArrayIndex.all());
                    INDArray f1 = featuresView.get(NDArrayIndex.all(),NDArrayIndex.interval(0,32));
                    f1.diviColumnVector(f1.norm2(1));
                    INDArray f2 = featuresView.get(NDArrayIndex.all(),NDArrayIndex.interval(32,64));
                    f2.diviColumnVector(f2.norm2(1));

                    INDArray encoding = encode(featuresView);

                    for (int j = 0; j < allFeatures.size(); j++) {
                        String label = featureNames.get(j);
                        INDArray averageEncoding = Transforms.unitVec(encoding.get(NDArrayIndex.interval(j*numSamples, j*numSamples+numSamples), NDArrayIndex.all()).mean(0));
                        if(averageEncoding.length()!=getVectorSize()) {
                            throw new RuntimeException("Wrong vector size: "+averageEncoding.length()+" != "+getVectorSize());
                        }
                        finalPredictionsMap.put(label, averageEncoding);
                        if(cnt.get()%100000==99999) {
                            System.gc();
                        }
                        if(cnt.getAndIncrement()%10000==9999) {
                            System.out.println("Finished "+cnt.get()+" filings out of "+filingsWithVecs.size()+". Incomplete: "+incomplete.get()+ " / "+cnt.get()+", Null Vae: "+nullVae.get()+" / "+incomplete.get());
                        }
                    }

                } else {
                    incomplete.getAndIncrement();
                }
            }
        });

        System.out.println("FINAL:: Finished "+cnt.get()+" filings out of "+filings.size()+". Incomplete: "+incomplete.get()+ " / "+cnt.get()+", Null Vae: "+nullVae.get()+" / "+incomplete.get());


        Map<String,INDArray> cpcVaeEncoderPredictions = pipelineManager.cpcvaePipelineManager.loadPredictions();

        // add cpc vectors
        cnt.set(0);
        incomplete.set(0);
        classCodes.forEach(cpc->{
            INDArray vaeVec = cpcVaeEncoderPredictions.get(cpc);
            if(vaeVec!=null) {
                INDArray cpc2Vec = cpc2VecMap.get(cpc);
                if(cpc2Vec!=null) {
                    INDArray feature = Nd4j.hstack(Transforms.unitVec(cpc2Vec),Transforms.unitVec(vaeVec)).reshape(new int[]{1,64});
                    INDArray encoding = encode(feature);
                    finalPredictionsMap.put(cpc,Transforms.unitVec(encoding));
                } else incomplete.getAndIncrement();
            } else incomplete.getAndIncrement();

            if(cnt.get()%50000==49999) {
                System.gc();
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Finished "+cnt.get()+" out of "+classCodes.size()+" cpcs. Incomplete: "+incomplete.get()+" / "+cnt.get());
            }
        });


        // add assignee vectors
        Map<String,List<String>> assigneeToCpcMap = assignees.parallelStream().collect(Collectors.toConcurrentMap(assignee->assignee,assignee->{
            return Stream.of(
                    Database.selectApplicationNumbersFromExactAssignee(assignee).stream().flatMap(asset->new AssetToCPCMap().getApplicationDataMap().getOrDefault(asset,Collections.emptySet()).stream()),
                    Database.selectPatentNumbersFromExactAssignee(assignee).stream().flatMap(asset->new AssetToCPCMap().getPatentDataMap().getOrDefault(asset,Collections.emptySet()).stream())
            ).flatMap(stream->stream).collect(Collectors.toList());
        }));
        cnt.set(0);
        incomplete.set(0);
        INDArray assigneeFeatures = Nd4j.create(assigneeSamples,32);
        INDArray assigneeVae = Nd4j.create(assigneeSamples,32);
        assignees.forEach(assignee->{
            INDArray vaeVec = cpcVaeEncoderPredictions.get(assignee);
            if(vaeVec!=null) {
                List<String> cpcs = assigneeToCpcMap.getOrDefault(assignee, Collections.emptyList()).stream().filter(cpc->cpc2VecMap.containsKey(cpc)).collect(Collectors.toList());
                if(cpcs.size()>0) {
                    for(int i = 0; i < assigneeSamples; i++) {
                        INDArray cpcVector = cpc2VecMap.get(cpcs.get(rand.nextInt(cpcs.size())));
                        assigneeFeatures.putRow(i,Transforms.unitVec(cpcVector));
                        assigneeVae.putRow(i,Transforms.unitVec(vaeVec));
                    }
                    INDArray encoding = encode(Nd4j.hstack(Transforms.unitVec(assigneeFeatures.mean(0)),Transforms.unitVec(assigneeVae.mean(0))));
                    finalPredictionsMap.put(assignee, Transforms.unitVec(encoding.mean(0)));

                } else {
                    incomplete.getAndIncrement();
                }

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

        System.out.println("Final prediction map size: "+finalPredictionsMap.size());

        return finalPredictionsMap;
    }

    public synchronized INDArray encode(INDArray input) {
        if(vaeNetwork==null) {
            vaeNetwork = getNetworks().get(VAE_NETWORK);
        }
        // Map<String,INDArray> activations = vaeNetwork.feedForward(input,false);
        return feedForwardToVertex(vaeNetwork,String.valueOf(encodingIdx),input); // activations.get(String.valueOf(encodingIdx));
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
    protected Map<String, ComputationGraph> buildNetworksForTraining() {
        System.out.println("Build model....");
        int hiddenLayerSize;
        int input1;
        int input2;
        int numHiddenLayers;

        if(CombinedSimilarityVAEPipelineManager.USE_DEEP_MODEL) {
            hiddenLayerSize = 128;
            input1 = WordCPC2VecPipelineManager.modelNameToVectorSizeMap.get(WordCPC2VecPipelineManager.DEEP_MODEL_NAME);
            input2 = DeepCPCVariationalAutoEncoderNN.VECTOR_SIZE;
            numHiddenLayers = 20;
        } else {
            hiddenLayerSize = 48;
            input1 = 32;
            input2 = 32;
            numHiddenLayers = 20;
        }

        Updater updater = Updater.RMSPROP;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        // build networks
        int i = 0;
        ComputationGraphConfiguration.GraphBuilder conf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(0.001)
                .activation(Activation.TANH)
                .graphBuilder()
                .addInputs("x")
                .setOutputs("y")
                .addLayer(String.valueOf(i), NNOptimizer.newDenseLayer(input1+input2,hiddenLayerSize).build(), "x")
                .addLayer(String.valueOf(i+1), NNOptimizer.newDenseLayer(input1+input2+hiddenLayerSize,hiddenLayerSize).build(), String.valueOf(i), "x");

        int increment = 1;

        i+=2;

        int t = i;
        //  hidden layers
        for(; i < t + numHiddenLayers*increment; i+=increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize);
            conf = conf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        }

        org.deeplearning4j.nn.conf.layers.Layer.Builder encoding = NNOptimizer.newDenseLayer(hiddenLayerSize+hiddenLayerSize,vectorSize);
        conf = conf.addLayer(String.valueOf(i),encoding.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        i++;
        org.deeplearning4j.nn.conf.layers.Layer.Builder decoding = NNOptimizer.newDenseLayer(vectorSize,hiddenLayerSize);
        conf = conf.addLayer(String.valueOf(i),decoding.build(), String.valueOf(i-increment));
        i++;

        t=i;

        //  hidden layers
        for(; i < t + numHiddenLayers*increment; i+=increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(i==t?(hiddenLayerSize+vectorSize):(hiddenLayerSize+hiddenLayerSize),hiddenLayerSize);
            conf = conf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        }

        // output layers
        OutputLayer.Builder outputLayer = NNOptimizer.newOutputLayer(hiddenLayerSize+hiddenLayerSize,input1+input2).lossFunction(lossFunction);

        conf = conf.addLayer("y",outputLayer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));

        vaeNetwork = new ComputationGraph(conf.build());
        vaeNetwork.init();

        //syncParams(wordCpc2Vec,cpcVecNet,encodingIdx);

        Map<String,ComputationGraph> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());
        nameToNetworkMap.put(VAE_NETWORK,vaeNetwork);
        return nameToNetworkMap;
    }

    @Override
    protected Map<String, ComputationGraph> updateNetworksBeforeTraining(Map<String, ComputationGraph> networkMap) {
        double newLearningRate = 0.0001;
        vaeNetwork = CGRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(VAE_NETWORK),newLearningRate,false);
        Map<String,ComputationGraph> updates = Collections.synchronizedMap(new HashMap<>());
        updates.put(VAE_NETWORK,vaeNetwork);
        return updates;
    }

    @Override
    protected Function<Void, Double> getTestFunction() {
        MultiDataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        List<MultiDataSet> validationDataSets = Collections.synchronizedList(new ArrayList<>());
        int valCount = 0;
        while(validationIterator.hasNext()&&valCount<20000) {
            MultiDataSet dataSet = validationIterator.next();
            INDArray vec = DEFAULT_LABEL_FUNCTION.apply(dataSet.getFeatures(0),dataSet.getFeatures(1));
            INDArray dates = dataSet.getFeatures(2);
            MultiDataSet finalDataSet = new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{vec}, new INDArray[]{vec,dates});
            validationDataSets.add(finalDataSet);
            valCount+=finalDataSet.getFeatures()[0].rows();
            //System.gc();
        }

        System.out.println("Num validation datasets: "+validationDataSets.size());

        return (v) -> {
            System.gc();
            return validationDataSets.stream().mapToDouble(ds->test(vaeNetwork,ds)).average().orElse(Double.NaN);
        };
    }



    @Override
    protected void train(MultiDataSet dataSet) {
        INDArray vec = DEFAULT_LABEL_FUNCTION.apply(dataSet.getFeatures(0),dataSet.getFeatures(1));
        INDArray dates = dataSet.getFeatures(2);
        MultiDataSet finalDataSet = new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{vec,dates}, new INDArray[]{vec,dates});
        vaeNetwork.fit(finalDataSet);
    }

    @Override
    protected Function<IterationListener, Void> setListenerFunction() {
        return listener -> {
            vaeNetwork.setListeners(listener);
            return null;
        };
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
