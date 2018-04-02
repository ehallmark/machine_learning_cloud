package models.similarity_models.combined_similarity_model;

import com.google.common.util.concurrent.AtomicDouble;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import lombok.Getter;
import models.similarity_models.deep_cpc_encoding_model.DeeperCPCVariationalAutoEncoderNN;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.graph.rnn.LastTimeStepVertex;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
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

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedCPC2Vec2DeeperVAEEncodingModel extends AbstractEncodingModel<ComputationGraph,CombinedCPC2Vec2DeeperVAEEncodingPipelineManager> {
    public static final String VAE_NETWORK = "vaeNet";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"combined_cpc2vec_tfidf_2_deeper_vae_model_data"); //new File("deep_cpc_2_vec_encoding_data");

    private List<ComputationGraph> networks;
    @Getter
    private ComputationGraph vaeNetwork;

    private int vectorSize;
    public CombinedCPC2Vec2DeeperVAEEncodingModel(CombinedCPC2Vec2DeeperVAEEncodingPipelineManager pipelineManager, String modelName, int vectorSize) {
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
            INDArray wordVecs = sampleWordVectors(words,maxSample,pipelineManager.getWord2Vec());
            if(wordVecs==null) return null;
            inputs.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.all()).assign(wordVecs.transpose());
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
        return 50;
    }


    @Override
    protected Map<String, ComputationGraph> buildNetworksForTraining() {
        Map<String, ComputationGraph> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());

        System.out.println("Build model....");


        networks = new ArrayList<>();

        // build networks
        double learningRate = 0.001;
        ComputationGraphConfiguration.GraphBuilder conf = createNetworkConf(learningRate);

        vaeNetwork = new ComputationGraph(conf.build());
        vaeNetwork.init();



        System.out.println("Conf: " + conf.toString());

        nameToNetworkMap.put(VAE_NETWORK, vaeNetwork);

        networks.add(vaeNetwork);
        System.out.println("Initial test: " +getTestFunction().apply(vaeNetwork));

        return nameToNetworkMap;
    }


    @Override
    protected Map<String, ComputationGraph> updateNetworksBeforeTraining(Map<String, ComputationGraph> networkMap) {
        // recreate net
        double newLearningRate = 0.0001;
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

            int valCount = 0;
            double score = 0d;
            int count = 0;
            while(validationIterator.hasNext()&&valCount<30000) {
                MultiDataSet dataSet = validationIterator.next();
                valCount+=dataSet.getFeatures()[0].shape()[0];
                try {
                    score += test(vaeNetwork, dataSet);
                } catch(Exception e) {
                    e.printStackTrace();
                    System.out.println("During testing...");
                }
                count++;
            }
            System.gc();
            validationIterator.reset();

            return score/count;
        };
    }

    private ComputationGraphConfiguration.GraphBuilder createNetworkConf(double learningRate) {
        int input1 = 128;
        int hiddenLayerSizeRnn = 256;
        int hiddenLayerSizeFF = 128;
        int input2 = DeeperCPCVariationalAutoEncoderNN.VECTOR_SIZE;


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
                .addLayer("rnn0", new GravesLSTM.Builder().dropOut(0.2).nIn(input1).nOut(hiddenLayerSizeRnn).build(), "x1")
                .addLayer("rnn1", new GravesLSTM.Builder().dropOut(0.2).nIn(hiddenLayerSizeRnn+input1).nOut(hiddenLayerSizeRnn).build(), "rnn0","x1")
                .addLayer("rnn2", new GravesLSTM.Builder().dropOut(0.2).nIn(hiddenLayerSizeRnn+hiddenLayerSizeRnn).nOut(hiddenLayerSizeRnn).build(), "rnn1","rnn0")
                .addVertex("r0", new LastTimeStepVertex("x1"),"rnn1")
                .addVertex("r1", new LastTimeStepVertex("x1"),"rnn2")
                .addLayer("ff0", new DenseLayer.Builder().dropOut(0.2).nIn(hiddenLayerSizeRnn*2).nOut(hiddenLayerSizeFF).build(), "r1","r0")
                .addLayer("ff1", new DenseLayer.Builder().dropOut(0.2).nIn(hiddenLayerSizeFF+hiddenLayerSizeRnn).nOut(hiddenLayerSizeFF).build(), "ff0","r1")
                .addLayer("ff2", new DenseLayer.Builder().dropOut(0.2).nIn(hiddenLayerSizeFF*2).nOut(hiddenLayerSizeFF).build(), "ff1","ff0")
                .addLayer("ff3", new DenseLayer.Builder().dropOut(0.2).nIn(hiddenLayerSizeFF*2).nOut(hiddenLayerSizeFF).build(), "ff2","ff1")
                .addLayer("ff4", new DenseLayer.Builder().dropOut(0.2).nIn(hiddenLayerSizeFF*2).nOut(hiddenLayerSizeFF).build(), "ff3","ff2")
                .addLayer("ff5", new DenseLayer.Builder().dropOut(0.2).nIn(hiddenLayerSizeFF*2).nOut(hiddenLayerSizeFF).build(), "ff4","ff3")
                .addLayer("ff6", new DenseLayer.Builder().dropOut(0.2).nIn(hiddenLayerSizeFF*2).nOut(hiddenLayerSizeFF).build(), "ff5","ff4")
                .addLayer("y1", new OutputLayer.Builder().activation(outputActivation).nIn(hiddenLayerSizeFF).lossFunction(lossFunction).nOut(input2).build(), "ff6")
                .setOutputs("y1")
                .backprop(true)
                .backpropType(BackpropType.Standard)
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
                AtomicInteger cnt = new AtomicInteger(0);
                AtomicDouble gradient = new AtomicDouble(0d);
                final double beta = 0.7;
                int gradientIterations = 400;
                while(dataSetIterator.hasNext()) {
                    MultiDataSet ds = dataSetIterator.next();
                    networks.forEach(vaeNetwork->{
                        try {
                            vaeNetwork.fit(ds);
                            if(cnt.getAndIncrement()%gradientIterations==gradientIterations-1) {
                                double grad = Transforms.abs(vaeNetwork.gradient().gradient(), false).meanNumber().doubleValue();
                                gradient.set((gradient.get()*beta)+(1d-beta)*grad);
                                System.out.println("Gradient: " + grad+", Avg Gradient: " + gradient.get());
                            }
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
