package models.similarity_models.combined_similarity_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import lombok.Getter;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.graph.L2NormalizeVertex;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
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
public class ReverseDeepCPC2VecEncodingModel extends AbstractEncodingModel<ComputationGraph,ReverseDeepCPC2VecEncodingPipelineManager> {
    public static final String VAE_NETWORK = "vaeNet";
    public static final File BASE_DIR = new File("reverse_deep_cpc_2_vec_encoding_data");
    private List<ComputationGraph> networks;
    @Getter
    private ComputationGraph vaeNetwork;

    private int vectorSize;
    public ReverseDeepCPC2VecEncodingModel(ReverseDeepCPC2VecEncodingPipelineManager pipelineManager, String modelName, int vectorSize) {
        super(pipelineManager,ComputationGraph.class,modelName);
        this.vectorSize=vectorSize;
    }

    public int getVectorSize() {
        return vectorSize;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        throw new UnsupportedOperationException("predict()");
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
        INDArray res = vaeNetwork.output(false, input)[0];
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
                    System.out.println("Shape of " + j + ": " + Arrays.toString(ReverseDeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(j), data3).shape()));
                    System.out.println("Shape of " + j + ": " + Arrays.toString(ReverseDeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(j), data5).shape()));
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
        int hiddenLayerSizeRNN = 64;
        int input1 = WordCPC2VecPipelineManager.modelNameToVectorSizeMap.get(WordCPC2VecPipelineManager.DEEP_MODEL_NAME);
        int maxSample = pipelineManager.getMaxSamples();
        int nLSTMLayers = 4;
        int layerOffset = 4;
        int outputIdx = layerOffset+nLSTMLayers;
        Updater updater = Updater.RMSPROP;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        Activation activation = Activation.TANH;
        Activation outputActivation = Activation.TANH;
        ComputationGraphConfiguration.GraphBuilder builder = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
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
                .addLayer("1", new DenseLayer.Builder().nIn(vectorSize).nOut(vectorSize*maxSample).build(), "0")
                .addVertex("2", new ReshapeVertex(-1,vectorSize,maxSample), "1")
                .addLayer("3", new GravesBidirectionalLSTM.Builder().nIn(vectorSize).nOut(hiddenLayerSizeRNN).build(), "2");

        for(int i = 0; i < nLSTMLayers; i++) {
            builder = builder.addLayer(String.valueOf(i+layerOffset), new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), String.valueOf(i+layerOffset-1));
        }

        return builder.addLayer("y1", new RnnOutputLayer.Builder().activation(outputActivation).nIn(vectorSize).lossFunction(lossFunction).nOut(input1).build(), String.valueOf(outputIdx-1))
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
