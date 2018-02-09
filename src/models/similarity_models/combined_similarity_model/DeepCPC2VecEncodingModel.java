package models.similarity_models.combined_similarity_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.optimize.nn_optimization.CGRefactorer;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import lombok.Getter;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.api.layers.IOutputLayer;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.graph.PreprocessorVertex;
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

    int numHiddenLayers = 3;
    int encodingIdx = numHiddenLayers+1;
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
        return 500;
    }


    @Override
    protected Map<String, ComputationGraph> buildNetworksForTraining() {
        Map<String, ComputationGraph> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());

        System.out.println("Build model....");
        int hiddenLayerSize = 96;
        int input1 = WordCPC2VecPipelineManager.modelNameToVectorSizeMap.get(WordCPC2VecPipelineManager.DEEP_MODEL_NAME);

        boolean useBatchNorm = false;

        Updater updater = Updater.RMSPROP;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        Activation activation = Activation.TANH;

        networks = new ArrayList<>();

        // build networks
        int i = 0;
        ComputationGraphConfiguration.GraphBuilder conf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(0.01)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .activation(activation)
                .graphBuilder()
                .addInputs("x1")
                .setOutputs("y1");

        if (useBatchNorm) {
            conf = conf.addLayer(String.valueOf(i), NNOptimizer.newBatchNormLayer(input1, input1).build(), "x1")
                    .addLayer(String.valueOf(i + 1), NNOptimizer.newGravesBidirectionalLSTMLayer(input1, hiddenLayerSize).build(), String.valueOf(i))
                    .addLayer(String.valueOf(i + 2), NNOptimizer.newBatchNormLayer(hiddenLayerSize, hiddenLayerSize).build(), String.valueOf(i + 1));
        } else {
            conf = conf
                    .addLayer(String.valueOf(i), NNOptimizer.newGravesBidirectionalLSTMLayer(input1 , hiddenLayerSize).build(), "x1");
        }

        int increment = useBatchNorm ? 2 : 1;

        i += useBatchNorm ? 3 : 1;

        int t = i;
       /* //  recurrent encoding
        for (; i < t + numHiddenLayers * increment; i += increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer;
            layer = NNOptimizer.newGravesBidirectionalLSTMLayer(hiddenLayerSize, hiddenLayerSize);

            org.deeplearning4j.nn.conf.layers.Layer.Builder norm = NNOptimizer.newBatchNormLayer(hiddenLayerSize, hiddenLayerSize);
            conf = conf.addLayer(String.valueOf(i), layer.build(), String.valueOf(i - 1));
            if (useBatchNorm) conf = conf.addLayer(String.valueOf(i + 1), norm.build(), String.valueOf(i));
        }
        */

       t = i;

        // dense hidden encoding
        for (; i < t + numHiddenLayers * increment; i += increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer;
            layer = NNOptimizer.newDenseLayer(hiddenLayerSize, hiddenLayerSize);

            org.deeplearning4j.nn.conf.layers.Layer.Builder norm = NNOptimizer.newBatchNormLayer(hiddenLayerSize, hiddenLayerSize);
            if(i==t) {
                conf = conf.addVertex("p1", new PreprocessorVertex(new RnnToFeedForwardPreProcessor()), String.valueOf(i-1))
                        .addVertex("r1", new ReshapeVertex(-1,pipelineManager.getMaxSamples()*hiddenLayerSize), "p1");
                conf = conf.addLayer(String.valueOf(i), NNOptimizer.newDenseLayer(pipelineManager.getMaxSamples()*hiddenLayerSize,hiddenLayerSize).build(),"r1");
            } else {
                conf = conf.addLayer(String.valueOf(i), layer.build(), String.valueOf(i - 1));
            }
            if (useBatchNorm) conf = conf.addLayer(String.valueOf(i + 1), norm.build(), String.valueOf(i));
        }

        org.deeplearning4j.nn.conf.layers.Layer.Builder encoding = NNOptimizer.newDenseLayer(hiddenLayerSize, vectorSize);

        org.deeplearning4j.nn.conf.layers.Layer.Builder eNorm = NNOptimizer.newBatchNormLayer(vectorSize, vectorSize);
        conf = conf.addLayer(String.valueOf(i), encoding.build(), String.valueOf(i - 1));
        if (useBatchNorm) conf = conf.addLayer(String.valueOf(i + 1), eNorm.build(), String.valueOf(i));
        i += increment;


        org.deeplearning4j.nn.conf.layers.Layer.Builder decoding = NNOptimizer.newDenseLayer(vectorSize, hiddenLayerSize);
        org.deeplearning4j.nn.conf.layers.Layer.Builder dNorm = NNOptimizer.newBatchNormLayer(hiddenLayerSize, hiddenLayerSize);
        conf = conf.addLayer(String.valueOf(i), decoding.build(), String.valueOf(i - 1));
        if (useBatchNorm) conf = conf.addLayer(String.valueOf(i + 1), dNorm.build(), String.valueOf(i));
        i += increment;

        t = i;

        //  dense hidden layers
        for (; i < t + numHiddenLayers * increment; i += increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer;
            layer = NNOptimizer.newDenseLayer(hiddenLayerSize, hiddenLayerSize);
            org.deeplearning4j.nn.conf.layers.Layer.Builder norm = NNOptimizer.newBatchNormLayer(hiddenLayerSize, hiddenLayerSize);
            if(i+increment>t+numHiddenLayers*increment) {
                // flat last layer for input to reshape vertex
                layer = NNOptimizer.newDenseLayer(hiddenLayerSize, pipelineManager.getMaxSamples()*hiddenLayerSize);
            }
            conf = conf.addLayer(String.valueOf(i), layer.build(), String.valueOf(i - 1));
            if (useBatchNorm) conf = conf.addLayer(String.valueOf(i + 1), norm.build(), String.valueOf(i));
        }

        t = i;

        //  recurrent hidden layers
        //for (; i < t + numHiddenLayers * increment; i += increment) {
        for (; i < t + 1 * increment; i += increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer;
            layer = NNOptimizer.newGravesBidirectionalLSTMLayer(hiddenLayerSize, hiddenLayerSize);

            org.deeplearning4j.nn.conf.layers.Layer.Builder norm = NNOptimizer.newBatchNormLayer(hiddenLayerSize, hiddenLayerSize);
            if(i==t) {
                conf = conf.addVertex("r2", new ReshapeVertex(-1,hiddenLayerSize), String.valueOf(i-1));
                conf = conf.addLayer(String.valueOf(i), layer.build(), new FeedForwardToRnnPreProcessor(), "r2");
            } else {
                conf = conf.addLayer(String.valueOf(i), layer.build(), String.valueOf(i - 1));
            }
            if (useBatchNorm) conf = conf.addLayer(String.valueOf(i + 1), norm.build(), String.valueOf(i));
        }

        // output layers
        RnnOutputLayer.Builder outputLayer = NNOptimizer.newRNNOutputLayer(hiddenLayerSize, input1).lossFunction(lossFunction).activation(Activation.TANH);

        conf = conf.addLayer("y1", outputLayer.build(), String.valueOf(i - 1));

        vaeNetwork = new ComputationGraph(conf.build());
        vaeNetwork.init();

        System.out.println("Conf: " + conf.toString());

        nameToNetworkMap.put(VAE_NETWORK, vaeNetwork);

        networks.add(vaeNetwork);


        return nameToNetworkMap;
    }

    @Override
    protected Map<String, ComputationGraph> updateNetworksBeforeTraining(Map<String, ComputationGraph> networkMap) {
        double newLearningRate = 0.000005;
        vaeNetwork = CGRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(VAE_NETWORK),newLearningRate,false);
        vaeNetwork = CGRefactorer.setInputPreprocessor(vaeNetwork,new RnnToFeedForwardPreProcessor(),1,new FeedForwardToRnnPreProcessor(),vaeNetwork.getNumLayers()-2,false);
        Map<String,ComputationGraph> updates = Collections.synchronizedMap(new HashMap<>());
        updates.put(VAE_NETWORK,vaeNetwork);
        networks = new ArrayList<>();
        networks.add(vaeNetwork);
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
            while(validationIterator.hasNext()&&valCount<20000) {
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

    public static double test(ComputationGraph net, MultiDataSet finalDataSet) {
        net.setLayerMaskArrays(finalDataSet.getFeaturesMaskArrays(),finalDataSet.getLabelsMaskArrays());
        double score = net.score(finalDataSet,false);
        net.clearLayerMaskArrays();
        return 1d+score/finalDataSet.getFeaturesMaskArray(0).shape()[1];
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
