package data_pipeline.optimize.parameters.impl;

import data_pipeline.optimize.parameters.distributions.ExponentialDoubleDistribution;
import data_pipeline.optimize.parameters.AveragingDoubleHyperParameter;
import data_pipeline.optimize.parameters.HyperParameter;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.Layer;

/**
 * Created by ehallmark on 11/10/17.
 */
public class LearningRateParameter extends AveragingDoubleHyperParameter {
    private double min;
    private double max;
    public LearningRateParameter(double min, double max) {
        super(new ExponentialDoubleDistribution(min,max,false));
        this.min=min;
        this.max=max;
    }

    @Override
    protected HyperParameter<Double> createNew(Double val) {
        LearningRateParameter param = new LearningRateParameter(min,max);
        param.set(val);
        return param;
    }

    @Override
    public NeuralNetConfiguration.Builder applyToNetwork(NeuralNetConfiguration.Builder networkBuilder) {
        return networkBuilder.learningRate(get());
    }

    @Override
    public Layer.Builder applyToLayer(Layer.Builder layerBuilder) {
        return layerBuilder;//.learningRate(get());
    }
}
