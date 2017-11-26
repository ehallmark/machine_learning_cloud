package models.similarity_models.word2vec_to_cpc_encoding_model;

import data_pipeline.helpers.Function3;
import data_pipeline.models.NeuralNetworkPredictionModel;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.MultiScoreReporter;
import data_pipeline.models.listeners.OptimizationScoreListener;
import data_pipeline.optimize.nn_optimization.MultiLayerNetworkWrapper;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.impl.ActivationFunctionParameter;
import data_pipeline.optimize.parameters.impl.LearningRateParameter;
import data_pipeline.optimize.parameters.impl.LossFunctionParameter;
import data_pipeline.optimize.parameters.impl.UpdaterParameter;
import models.NDArrayHelper;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
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

import static data_pipeline.optimize.nn_optimization.NNOptimizer.*;

/**
 * Created by Evan on 10/29/2017.
 */
public class Word2VecToCPCEncodingNN extends NeuralNetworkPredictionModel<INDArray> {
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"word2vec_to_cpc_deep_nn_model_data");

    private Word2VecToCPCPipelineManager pipelineManager;
    public Word2VecToCPCEncodingNN(Word2VecToCPCPipelineManager pipelineManager, String modelName) {
        super(modelName);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets,List<String> assignees, List<String> classCodes) {
        throw new UnsupportedOperationException("This model does not make asset predictions.");
    }

    @Override
    public void train(int nEpochs) {
        final int printIterations = 100;
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        DataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();

        System.out.println("Building validation matrix...");
        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        List<INDArray> partialValidationInputs = new ArrayList<>();
        List<INDArray> partialValidationLabels = new ArrayList<>();
        while(validationIterator.hasNext()) {
            DataSet ds = validationIterator.next();
            partialValidationInputs.add(ds.getFeatureMatrix());
            partialValidationLabels.add(ds.getLabels());
        }
        INDArray validationInputs = Nd4j.vstack(partialValidationInputs);
        INDArray validationOutputs = Nd4j.vstack(partialValidationLabels);
        Function<MultiLayerNetwork,Double> testErrorFunction = (net) -> {
            return test(validationInputs,validationOutputs,net);
        };

        Function3<MultiLayerNetwork,LocalDateTime,Double,Void> saveFunction = (net,datetime, score) -> {
            try {
                save(datetime,score,net);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        // Optimizer
        int numNetworks = 1;
        final int outputSize = CPCVariationalAutoEncoderNN.VECTOR_SIZE;
        final int inputSize = pipelineManager.getWord2Vec().getLayerSize()*3;
        final int hiddenLayerSize1 = inputSize;
        final int hiddenLayerSize2 = (inputSize+outputSize)/2;
        final MultiScoreReporter reporter = new MultiScoreReporter(numNetworks, 1);

        if(net==null) {
            NNOptimizer optimizer;
            optimizer = new NNOptimizer(
                    getPreModel(),
                    getLayerModels(inputSize, hiddenLayerSize1, hiddenLayerSize2, outputSize),
                    getModelParameters(),
                    getLayerParameters(),
                    numNetworks,
                    net -> {
                        IterationListener listener = new OptimizationScoreListener(reporter, net, printIterations, testErrorFunction, saveFunction);
                        net.getNet().setListeners(listener);
                        return null;
                    }
            );
            // initialize optimizer
            optimizer.initNetworkSamples();


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
        } else {
            double newLearningRate = 0.01;
           // double newRegularization = 1e-4;
           // boolean newUseRegularization = true;
            System.out.println("Updating network with learning rate: "+newLearningRate);
            net = NNRefactorer.updateNetworkLearningRate(net,newLearningRate,false);
            //  System.out.println("Updating network with new regularization: "+newRegularization);
            //  net = NNRefactorer.updateNetworkRegularization(net,newUseRegularization,newRegularization,false);

            System.out.println("Conf: "+net.getLayerWiseConfigurations().toYaml());

            MultiLayerNetworkWrapper netWrapper = new MultiLayerNetworkWrapper(net,Collections.emptyList());
            IterationListener listener = new OptimizationScoreListener(reporter, netWrapper, printIterations, testErrorFunction, saveFunction);
            net.setListeners(listener);
            for (int i = 0; i < nEpochs; i++) {
                System.out.println("Starting epoch {" + (i + 1) + "} of {" + nEpochs + "}");
                try {
                    net.fit(trainIter);
                } catch (StoppingConditionMetException s) {
                    System.out.println("Stopping condition met");
                }
                if (stoppingCondition.get()) {
                    break;
                }
                trainIter.reset();
            }
        }
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    private double test(INDArray input, INDArray output, MultiLayerNetwork net) {
        INDArray predictions = net.activateSelectedLayers(0,net.getnLayers()-1,input);
        double similarity = NDArrayHelper.sumOfCosineSimByRow(predictions,output);
        return 1d - (similarity/input.rows());
    }

    private List<HyperParameter> getModelParameters() {
        return Arrays.asList(
                new LearningRateParameter(0.15,0.15),
                new UpdaterParameter(Arrays.asList(
                        //Updater.RMSPROP,
                        Updater.ADAM
                )),
                new ActivationFunctionParameter(Arrays.asList(
                        Activation.LEAKYRELU//,
                       // Activation.TANH
                ))
        );
    }

    private List<List<HyperParameter>> getLayerParameters() {
        return Arrays.asList(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                // output layer
                Arrays.asList(
                        new ActivationFunctionParameter(Arrays.asList(
                                Activation.IDENTITY//,
                                //Activation.TANH
                        )),
                        new LossFunctionParameter(Arrays.asList(
                                LossFunctions.LossFunction.COSINE_PROXIMITY//,
                                //LossFunctions.LossFunction.MSE
                        ))
                )
        );
    }

    private NeuralNetConfiguration getPreModel() {
        return NNOptimizer.defaultNetworkConfig();
    }

    private List<Layer.Builder> getLayerModels(int inputSize, int hiddenLayerSize1, int hiddenLayerSize2, int outputSize) {
        return Arrays.asList(
                newDenseLayer(inputSize,hiddenLayerSize1),
                newBatchNormLayer(hiddenLayerSize1,hiddenLayerSize1),
                newDenseLayer(hiddenLayerSize1,hiddenLayerSize1),
                newBatchNormLayer(hiddenLayerSize1,hiddenLayerSize1),
                newDenseLayer(hiddenLayerSize1,hiddenLayerSize2),
                newBatchNormLayer(hiddenLayerSize2,hiddenLayerSize2),
                newDenseLayer(hiddenLayerSize2,hiddenLayerSize2),
                newBatchNormLayer(hiddenLayerSize2,hiddenLayerSize2),
                newOutputLayer(hiddenLayerSize2,outputSize)
        );
    }

}