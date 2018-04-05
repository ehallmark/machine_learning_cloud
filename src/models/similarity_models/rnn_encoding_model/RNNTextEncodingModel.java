package models.similarity_models.rnn_encoding_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.helpers.Function2;
import data_pipeline.models.BaseTrainablePredictionModel;
import data_pipeline.models.ComputationGraphPredictionModel;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.optimize.nn_optimization.LayerWrapper;
import data_pipeline.optimize.nn_optimization.VertexWrapper;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.vectorize.DataSetManager;
import models.similarity_models.cpc_encoding_model.CPCDataSetIterator;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.similarity_models.deep_cpc_encoding_model.DeeperCPCIndexMap;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/26/17.
 */
public class RNNTextEncodingModel extends BaseTrainablePredictionModel<INDArray,ComputationGraph> {
    public static final int VECTOR_SIZE = 32;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"rnn_text_encoding_model_data");

    private final int inputSize;
    private final int vectorSize;
    private final RNNTextEncodingPipelineManager pipelineManager;
    public RNNTextEncodingModel(RNNTextEncodingPipelineManager pipelineManager, String modelName, int inputSize, int vectorSize) {
        super(modelName);
        this.pipelineManager=pipelineManager;
        this.inputSize=inputSize;
        this.vectorSize=vectorSize;
    }



    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    @Override
    protected void saveNet(ComputationGraph net, File file) throws IOException {
        ModelSerializer.writeModel(net,file.getAbsolutePath(),true);
    }

    @Override
    protected void restoreFromFile(File modelFile) throws IOException {
        net = ModelSerializer.restoreComputationGraph(modelFile,true);
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null; // MAJOR TODO
    }

    private ComputationGraphConfiguration getConf(double learningRate, int inputSize, int vectorSize) {
        int rngSeed = 69;
        Activation activation = Activation.TANH;
        Nd4j.getRandom().setSeed(rngSeed);

        int hiddenLayerSize = 256;

        Map<Integer,Double> iterationLearningRate = new HashMap<>();
        iterationLearningRate.put(0,learningRate);
        //iterationLearningRate.put(1000,learningRate/2);
        //iterationLearningRate.put(5000,learningRate/4);

        return new NeuralNetConfiguration.Builder()
                .seed(rngSeed)
                .learningRate(learningRate)
                .learningRateDecayPolicy(LearningRatePolicy.Schedule)
                .learningRateSchedule(iterationLearningRate)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                //.updater(Updater.RMSPROP)
                .updater(Updater.ADAM)
                .miniBatch(true)
                .activation(activation)
                .weightInit(WeightInit.XAVIER)
                //.gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                //.gradientNormalizationThreshold(1d)
                //.regularization(true).l2(1e-4)
                .graphBuilder()
                .addInputs("x")
                .addLayer("l1", new GravesLSTM.Builder().nIn(inputSize).nOut(hiddenLayerSize).build(),"x")
                .addLayer("v", new GravesLSTM.Builder().nIn(hiddenLayerSize).nOut(vectorSize).build(), "l1")
                .addLayer("l3", new GravesLSTM.Builder().nIn(vectorSize).nOut(hiddenLayerSize).build(), "v")
                .addLayer("y", new RnnOutputLayer.Builder().lossFunction(LossFunctions.LossFunction.COSINE_PROXIMITY).nIn(hiddenLayerSize).nOut(inputSize).build(),"l3")
                .pretrain(false).backprop(true).build();
    }

    @Override
    public void train(int nEpochs) {
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        MultiDataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();
        final int printIterations = 100;

        if(net==null) {
            final double learningRate = 0.001;
            net = new ComputationGraph(getConf(learningRate,inputSize,vectorSize));
            net.init();
        } else {
            final double learningRate =  0.00001;
            INDArray params = net.params();
            net = new ComputationGraph(getConf(learningRate,inputSize,vectorSize));
            net.init(params,false);

        }

        System.out.println("Building validation matrix...");
        MultiDataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        Function<Object,Double> testErrorFunction = (v) -> {
            double total = 0d;
            int count = 0;
            while(validationIterator.hasNext()&&count<10) {
                double score = net.score(validationIterator.next(),false);
                count++;
                total+=score;
            }
            System.gc();
            validationIterator.reset();
            return 1D+(total/count);
        };

        Function<Object,Double> trainErrorFunction = (v) -> {
            return 0d;//test(pipelineManager.getDatasetManager().getTrainingIterator(10000/pipelineManager.getBatchSize()), vae);
        };

        System.out.println(", Initial test: "+testErrorFunction.apply(net));

        Function2<LocalDateTime,Double,Void> saveFunction = (datetime,score) -> {
            try {
                save(datetime,score);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        IterationListener listener = new DefaultScoreListener(printIterations, testErrorFunction, trainErrorFunction, saveFunction, stoppingCondition);
        net.setListeners(listener);

        //AtomicInteger gcIter = new AtomicInteger(0);
        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            try {
                net.fit(trainIter);
                /*
                while(trainIter.hasNext()) {
                    DataSet ds = trainIter.next();
                    net.fit(ds);
                   // if(gcIter.getAndIncrement()%100==0)System.gc();
                }*/
            } catch(StoppingConditionMetException s) {
                System.out.println("Stopping condition met");
            }
            if(stoppingCondition.get()) {
                break;
            }
            trainIter.reset();
        }
    }



}