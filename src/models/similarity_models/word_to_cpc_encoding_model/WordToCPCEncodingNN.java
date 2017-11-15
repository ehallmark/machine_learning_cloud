package models.similarity_models.word_to_cpc_encoding_model;

import data_pipeline.helpers.Function2;
import data_pipeline.models.TrainablePredictionModel;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import static data_pipeline.optimize.nn_optimization.NNOptimizer.*;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.impl.ActivationFunctionParameter;
import data_pipeline.optimize.parameters.impl.LearningRateParameter;
import data_pipeline.optimize.parameters.impl.LossFunctionParameter;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.similarity_models.signatures.NDArrayHelper;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by Evan on 10/29/2017.
 */
public class WordToCPCEncodingNN extends TrainablePredictionModel<INDArray> {
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"word_to_cpc_deep_nn_model_data");

    private WordToCPCPipelineManager pipelineManager;
    public WordToCPCEncodingNN(WordToCPCPipelineManager pipelineManager, String modelName) {
        super(modelName);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets) {
        throw new UnsupportedOperationException("This model does not make asset predictions.");
    }

    @Override
    public void train(int nEpochs) {
        final int printIterations = 100;
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        DataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();

        if(net==null) {
            /*int seed = 10;
            //final int hiddenLayerSize1 = 1024;
            final int hiddenLayerSize = 512;
            final int outputSize = CPCVariationalAutoEncoderNN.VECTOR_SIZE;
            final int vocabSize = pipelineManager.getWordToIdxMap().size();
            Nd4j.getRandom().setSeed(seed);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .activation(Activation.TANH)
                    .updater(Updater.ADAM)
                    //.updater(Updater.RMSPROP)
                    //.rmsDecay(0.95)
                    .seed(seed)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .learningRate(0.05)
                    .miniBatch(true)
                    .weightInit(WeightInit.XAVIER)
                    //.regularization(true).l2(1e-4)
                    //.dropOut(0.5)
                    .list()
                    .layer(0, new DenseLayer.Builder()
                            .nIn(vocabSize)
                            .nOut(hiddenLayerSize)
                            .build()
                    ).layer(1, new BatchNormalization.Builder()
                            .nIn(hiddenLayerSize)
                            .nOut(hiddenLayerSize)
                            .minibatch(true)
                            .build()
                    ).layer(2, new DenseLayer.Builder()
                            .nIn(hiddenLayerSize)
                            .nOut(hiddenLayerSize)
                            .build()
                    ).layer(3, new BatchNormalization.Builder()
                            .nIn(hiddenLayerSize)
                            .nOut(hiddenLayerSize)
                            .minibatch(true)
                            .build()
                    ).layer(4, new OutputLayer.Builder()
                            .lossFunction(LossFunctions.LossFunction.COSINE_PROXIMITY)
                            .activation(Activation.IDENTITY)
                            .nIn(hiddenLayerSize)
                            .nOut(outputSize)
                            .build()
                    ).build();

            net = new MultiLayerNetwork(conf);
            net.init(); */

            // try nn optimizer
        }

        System.out.println("Building validation matrix...");
        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        int cnt = 0;
        List<INDArray> partialValidationInputs = new ArrayList<>();
        List<INDArray> partialValidationLabels = new ArrayList<>();
        while(cnt<10000&&validationIterator.hasNext()) {
            DataSet ds = validationIterator.next();
            partialValidationInputs.add(ds.getFeatureMatrix());
            partialValidationLabels.add(ds.getLabels());
            cnt+=ds.getFeatureMatrix().rows();
        }
        INDArray validationInputs = Nd4j.vstack(partialValidationInputs);
        INDArray validationOutputs = Nd4j.vstack(partialValidationLabels);
        Function<Void,Double> testErrorFunction = (v) -> {
            return test(validationInputs,validationOutputs);
        };

        Function<Void,Double> trainErrorFunction = (v) -> {
            return 0d;//test(pipelineManager.getDatasetManager().getTrainingIterator(10000/pipelineManager.getBatchSize()), vae);
        };

        Function2<LocalDateTime,Double,Void> saveFunction = (datetime, score) -> {
            try {
                save(datetime,score);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        // Optimizer
        int numNetworks = 10;
        final int hiddenLayerSize = 512;
        final int outputSize = CPCVariationalAutoEncoderNN.VECTOR_SIZE;
        final int inputSize = pipelineManager.getWordToIdxMap().size();
        NNOptimizer optimizer = new NNOptimizer(
                getPreModel(),
                getLayerModels(inputSize,hiddenLayerSize,outputSize),
                getModelParameters(),
                getLayerParameters(),
                numNetworks,
                net -> {
                    IterationListener listener = new DefaultScoreListener(printIterations, testErrorFunction, trainErrorFunction, saveFunction, stoppingCondition);
                    net.setListeners(listener);
                    return null;
                }
        );


        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            while(trainIter.hasNext()) {
                try {
                    optimizer.train(trainIter.next());
                } catch (StoppingConditionMetException s) {
                    System.out.println("Stopping condition met");
                }
                if (stoppingCondition.get()) {
                    break;
                }
            }
            if(stoppingCondition.get()) break;
            trainIter.reset();
        }

        /*
        IterationListener listener = new DefaultScoreListener(printIterations, testErrorFunction, trainErrorFunction, saveFunction, stoppingCondition);
        net.setListeners(listener);

        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            try {
                net.fit(trainIter);
            } catch(StoppingConditionMetException s) {
                System.out.println("Stopping condition met");
            }
            if(stoppingCondition.get()) {
                break;
            }
            trainIter.reset();
        }
        */
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    private double test(INDArray input, INDArray output) {
        INDArray predictions = net.activateSelectedLayers(0,net.getnLayers()-1,input);
        double similarity = NDArrayHelper.sumOfCosineSimByRow(predictions,output);
        return 1d - (similarity/input.rows());
    }

    private List<HyperParameter> getModelParameters() {
        return Arrays.asList(
                new LearningRateParameter(0.0001,0.1),
                new ActivationFunctionParameter(Arrays.asList(
                        Activation.LEAKYRELU,
                        Activation.RRELU,
                        Activation.HARDTANH,
                        Activation.TANH
                ))
        );
    }

    private List<List<HyperParameter>> getLayerParameters() {
        return Arrays.asList(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                // output layer
                Arrays.asList(
                        new ActivationFunctionParameter(Arrays.asList(
                                Activation.IDENTITY,
                                Activation.TANH,
                                Activation.HARDTANH
                        )),
                        new LossFunctionParameter(Arrays.asList(
                                LossFunctions.LossFunction.COSINE_PROXIMITY,
                                LossFunctions.LossFunction.MCXENT,
                                LossFunctions.LossFunction.MSE,
                                LossFunctions.LossFunction.KL_DIVERGENCE
                        ))
                )
        );
    }

    private NeuralNetConfiguration getPreModel() {
        return NNOptimizer.defaultNetworkConfig();
    }

    private List<Layer.Builder> getLayerModels(int inputSize, int hiddenLayerSize, int outputSize) {
        return Arrays.asList(
                newDenseLayer(inputSize,hiddenLayerSize),
                newBatchNormLayer(hiddenLayerSize,hiddenLayerSize),
                newDenseLayer(hiddenLayerSize,hiddenLayerSize),
                newBatchNormLayer(hiddenLayerSize,hiddenLayerSize),
                newOutputLayer(hiddenLayerSize,outputSize)
        );
    }

}
