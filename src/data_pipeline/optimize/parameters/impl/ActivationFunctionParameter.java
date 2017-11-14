package data_pipeline.optimize.parameters.impl;

import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.SimpleDiscreteHyperParameter;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.List;

/**
 * Created by ehallmark on 11/10/17.
 */
public class ActivationFunctionParameter extends SimpleDiscreteHyperParameter<Activation> {
    public ActivationFunctionParameter(List<Activation> functions) {
        super(functions);
    }

    @Override
    protected HyperParameter<Activation> createNew(Activation val) {
        ActivationFunctionParameter param = new ActivationFunctionParameter(values);
        param.set(val);
        return param;
    }

    @Override
    public Layer.Builder applyToLayer(Layer.Builder layerBuilder) {
        return layerBuilder.activation(get());
    }

    @Override
    public NeuralNetConfiguration.Builder applyToNetwork(NeuralNetConfiguration.Builder networkBuilder) {
        return networkBuilder.activation(get());
    }
}
