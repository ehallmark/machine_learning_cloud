package data_pipeline.optimize.parameters.impl;

import data_pipeline.optimize.parameters.SimpleDiscreteHyperParameter;
import data_pipeline.optimize.parameters.distributions.ExponentialDoubleDistribution;
import data_pipeline.optimize.parameters.AveragingDoubleHyperParameter;
import data_pipeline.optimize.parameters.HyperParameter;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.List;

/**
 * Created by ehallmark on 11/10/17.
 */
public class LossFunctionParameter extends SimpleDiscreteHyperParameter<LossFunctions.LossFunction> {
    public LossFunctionParameter(List<LossFunctions.LossFunction> functions) {
        super(functions);
    }

    @Override
    protected HyperParameter<LossFunctions.LossFunction> createNew(LossFunctions.LossFunction val) {
        LossFunctionParameter param = new LossFunctionParameter(values);
        param.set(val);
        return param;
    }

    @Override
    public Layer.Builder applyToLayer(Layer.Builder layerBuilder) {
        // make sure it is output layer
        if(layerBuilder instanceof OutputLayer.Builder) {
            return ((OutputLayer.Builder)layerBuilder).lossFunction(get());
        } else return layerBuilder; // otherwise do nothing
    }

    @Override
    public NeuralNetConfiguration.Builder applyToNetwork(NeuralNetConfiguration.Builder networkBuilder) {
        return networkBuilder; // DO NOTHING
    }
}
