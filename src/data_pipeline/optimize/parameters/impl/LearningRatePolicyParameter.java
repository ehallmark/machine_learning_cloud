package data_pipeline.optimize.parameters.impl;

import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.SimpleDiscreteHyperParameter;
import org.deeplearning4j.nn.conf.LearningRatePolicy;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.Layer;

import java.util.List;

/**
 * Created by ehallmark on 11/10/17.
 */
public class LearningRatePolicyParameter extends SimpleDiscreteHyperParameter<LearningRatePolicy> {
    public LearningRatePolicyParameter(List<LearningRatePolicy> functions) {
        super(functions);
    }

    @Override
    protected HyperParameter<LearningRatePolicy> createNew(LearningRatePolicy val) {
        LearningRatePolicyParameter param = new LearningRatePolicyParameter(values);
        param.set(val);
        return param;
    }

    @Override
    public Layer.Builder applyToLayer(Layer.Builder layerBuilder) {
        return layerBuilder.learningRateDecayPolicy(get());
    }

    @Override
    public NeuralNetConfiguration.Builder applyToNetwork(NeuralNetConfiguration.Builder networkBuilder) {
        return networkBuilder.learningRateDecayPolicy(get());
    }
}
