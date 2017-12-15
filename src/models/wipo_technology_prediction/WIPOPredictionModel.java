package models.wipo_technology_prediction;

import data_pipeline.models.ComputationGraphPredictionModel;
import data_pipeline.optimize.nn_optimization.LayerWrapper;
import data_pipeline.optimize.nn_optimization.VertexWrapper;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.impl.ActivationFunctionParameter;
import data_pipeline.optimize.parameters.impl.LearningRateParameter;
import data_pipeline.optimize.parameters.impl.LossFunctionParameter;
import data_pipeline.optimize.parameters.impl.UpdaterParameter;
import org.deeplearning4j.nn.conf.Updater;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static data_pipeline.optimize.nn_optimization.NNOptimizer.*;

/**
 * Created by ehallmark on 12/13/17.
 */
public class WIPOPredictionModel extends ComputationGraphPredictionModel<String> {
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"wipo_prediction_model_data");

    public WIPOPredictionModel(WIPOPredictionPipelineManager pipelineManager, String modelName) {
        super(modelName,pipelineManager);
    }

    @Override
    public Map<String, String> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    @Override
    protected List<List<HyperParameter>> getLayerParameters() {
        return Arrays.asList(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                // output layer
                Arrays.asList(
                        new ActivationFunctionParameter(Arrays.asList(
                                Activation.SOFTMAX
                        )),
                        new LossFunctionParameter(Arrays.asList(
                                LossFunctions.LossFunction.XENT
                        ))
                )
        );
    }


    @Override
    protected List<LayerWrapper> getLayerModels(int inputSize, int hiddenLayerSize, int outputSize) {
        return Arrays.asList(
                new LayerWrapper("l1", newDenseLayer(inputSize,hiddenLayerSize), "x"),
                new LayerWrapper("l2", newBatchNormLayer(hiddenLayerSize,hiddenLayerSize), "l1"),
                new LayerWrapper("l3", newDenseLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize),"x","l2"),
                new LayerWrapper("l4", newBatchNormLayer(hiddenLayerSize,hiddenLayerSize),"l3"),
                new LayerWrapper("l5", newDenseLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize),"l2","l4"),
                new LayerWrapper("l6", newBatchNormLayer(hiddenLayerSize,hiddenLayerSize),"l5"),
                new LayerWrapper("y", newOutputLayer(hiddenLayerSize+hiddenLayerSize,outputSize), "l4","l6")
        );
    }

    @Override
    protected List<VertexWrapper> getVertexModels() {
        return Arrays.asList(
                //new VertexWrapper("rnn_to_dense1",new PreprocessorVertex(new RnnToFeedForwardPreProcessor()),"l3"),
                //new VertexWrapper("rnn_to_dense2",new PreprocessorVertex(new RnnToFeedForwardPreProcessor()),"l4")
        );
    }


    @Override
    protected List<HyperParameter> getModelParameters() {
        return Arrays.asList(
                new LearningRateParameter(0.005,0.25),
                //new L2RegularizationParameter(1e-4,1e-4),
                new UpdaterParameter(Arrays.asList(
                        Updater.RMSPROP//,
                        //Updater.ADAM
                )),
                new ActivationFunctionParameter(Arrays.asList(
                        Activation.TANH//,
                        //Activation.LEAKYRELU
                ))
        );
    }

    @Override
    protected int getNumNetworks() {
        return 3;
    }

    @Override
    protected int getHiddenLayerSize() {
        return 96;
    }

    @Override
    protected double getNewLearningRate() {
        return 0.0005;
    }
}
