package models.similarity_models.combined_similarity_model;

import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import lombok.Getter;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.LearningRatePolicy;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.graph.SubsetVertex;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
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
import seeding.Constants;
import tools.ReshapeVertex;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedCPC2Vec2VAEEncodingModel extends AbstractEncodingModel<ComputationGraph,CombinedCPC2Vec2VAEEncodingPipelineManager> {
    public static final String VAE_NETWORK = "vaeNet";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"combined_cpc2vec_2_vae_model_data"); //new File("deep_cpc_2_vec_encoding_data");

    private List<ComputationGraph> networks;
    @Getter
    private ComputationGraph vaeNetwork;

    private int vectorSize;
    public CombinedCPC2Vec2VAEEncodingModel(CombinedCPC2Vec2VAEEncodingPipelineManager pipelineManager, String modelName, int vectorSize) {
        super(pipelineManager,ComputationGraph.class,modelName);
        this.vectorSize=vectorSize;
    }

    public int getVectorSize() {
        return vectorSize;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
       throw new UnsupportedOperationException("Predictions not support with this model.");
    }


    public synchronized INDArray encodeText(List<String> words, int samples) {
        int maxSample = pipelineManager.getMaxSample();
        INDArray inputs = Nd4j.create(samples,vectorSize,maxSample);

        for(int i = 0; i < samples; i ++) { // TODO speed this up (i.e remove loop)
            inputs.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.all()).assign(sampleWordVectors(words,maxSample,pipelineManager.getWord2Vec()).transpose());
        }

        //System.out.println("Sampled word vectors shape: "+Arrays.toString(inputs.shape()));
        return encodeText(inputs);
    }

    public synchronized INDArray encodeText(INDArray inputs) {
        if(vaeNetwork==null) {
            updateNetworksBeforeTraining(getNet().getNameToNetworkMap());
        }
        return Transforms.unitVec(vaeNetwork.output(false,inputs)[0].mean(0));
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

            INDArray data3 = Nd4j.randn(new int[]{3, input1});
            INDArray data5 = Nd4j.randn(new int[]{5, input1});


            for (int j = 1; j < 9; j++) {
                try {
                    System.out.println("Shape of " + j + ": " + Arrays.toString(CombinedCPC2Vec2VAEEncodingModel.feedForwardToVertex(graph, String.valueOf(j), data3).shape()));
                    System.out.println("Shape of " + j + ": " + Arrays.toString(CombinedCPC2Vec2VAEEncodingModel.feedForwardToVertex(graph, String.valueOf(j), data5).shape()));
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
        double newLearningRate = 0.005;
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
        //System.out.println("Initial test: " +getTestFunction().apply(vaeNetwork));
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

    private ComputationGraphConfiguration.GraphBuilder createNetworkConf(double learningRate) {
        int input1 = WordCPC2VecPipelineManager.modelNameToVectorSizeMap.get(WordCPC2VecPipelineManager.DEEP_MODEL_NAME);
        int hiddenLayerSizeFF1 = 320;
        int input2 = DeepCPCVariationalAutoEncoderNN.VECTOR_SIZE;

        int maxSample = pipelineManager.getMaxSample();

        Updater updater = Updater.RMSPROP;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        Activation activation = Activation.TANH;
        Activation outputActivation = Activation.IDENTITY;
        Map<Integer,Double> learningRateSchedule = new HashMap<>();
        learningRateSchedule.put(0,learningRate);
        learningRateSchedule.put(50000,learningRate/2);
        learningRateSchedule.put(100000,learningRate/5);
        learningRateSchedule.put(200000,learningRate/10);
        learningRateSchedule.put(400000,learningRate/25);
        return new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(learningRate)
                .learningRateDecayPolicy(LearningRatePolicy.Schedule)
                .learningRateSchedule(learningRateSchedule)
                .regularization(true).l2(0.0001)
                //.convolutionMode(ConvolutionMode.Same) //This is important so we can 'stack' the results later
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .activation(activation)
                .graphBuilder()
                .addInputs("x1")
                .addVertex("reshape", new ReshapeVertex(-1,input1*maxSample), "x1")
                .addLayer("6", new DenseLayer.Builder().nIn(input1*maxSample).nOut(hiddenLayerSizeFF1).build(), "reshape")
                .addLayer("7", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1+input1*maxSample).nOut(hiddenLayerSizeFF1).build(), "6", "reshape")
                .addLayer("8", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "7","6")
                .addLayer("9", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "8","7")
                .addLayer("10", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "9","8")
                .addLayer("11", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "10","9")
                .addLayer("12", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "11","10")
                .addLayer("13", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "12","11")
                .addLayer("14", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "13","12")
                .addLayer("15", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "14","13")
                .addLayer("16", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "15","14")
                .addLayer("17", new DenseLayer.Builder().nIn(hiddenLayerSizeFF1*2).nOut(hiddenLayerSizeFF1).build(), "16","15")
                .addLayer("y1", new OutputLayer.Builder().activation(outputActivation).nIn(hiddenLayerSizeFF1*2).lossFunction(lossFunction).nOut(input2).build(), "17", "16")
                .setOutputs("y1")
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
