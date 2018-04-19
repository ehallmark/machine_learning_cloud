package models.similarity_models.rnn_encoding_model;

import data_pipeline.helpers.Function2;
import data_pipeline.models.BaseTrainablePredictionModel;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by ehallmark on 10/26/17.
 */
public class RNNTextEncodingModel extends BaseTrainablePredictionModel<INDArray,ComputationGraph> {
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
    protected File getModelFile(LocalDateTime dateTime) {
        return new File(getModelBaseDirectory(), modelName);
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null; // MAJOR TODO
    }

    private ComputationGraphConfiguration getConf(double learningRate, int inputSize, int vectorSize) {
        int rngSeed = 69;
        Activation activation = Activation.TANH;
        Nd4j.getRandom().setSeed(rngSeed);

        int hiddenLayerSize = 128;

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
                .setOutputs("y")
                .pretrain(false).backprop(true).build();
    }

    @Override
    public void train(int nEpochs) {
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        MultiDataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();
        final int printIterations = 1000;

        if(net==null) {
            final double learningRate = 0.01;
            net = new ComputationGraph(getConf(learningRate,inputSize,vectorSize));
            net.init();
        } else {
            final double learningRate =  0.00001;
            INDArray params = net.params();
            net = new ComputationGraph(getConf(learningRate,inputSize,vectorSize));
            net.init(params,false);

        }

        List<MultiDataSet> validationDatasets = new ArrayList<>();
        int count = 0;
        MultiDataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        while(validationIterator.hasNext()&&count<5) {
            validationDatasets.add(validationIterator.next());
            count++;
        }
        System.out.println("Building validation matrix...");
        Function<Object,Double> testErrorFunction = (v) -> {
            double total = 0d;
            for(MultiDataSet ds : validationDatasets) {
                double score = net.score(ds,false);
                total+=score;
            }
            System.gc();
            validationIterator.reset();
            return (total/validationDatasets.size());
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
