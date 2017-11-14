package data_pipeline.optimize.parameters.impl;

import data_pipeline.optimize.parameters.AveragingDoubleHyperParameter;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.distributions.ExponentialDoubleDistribution;
import data_pipeline.optimize.parameters.distributions.UniformDoubleDistribution;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.Layer;

/**
 * Created by ehallmark on 11/10/17.
 */
public class DropoutParameter extends AveragingDoubleHyperParameter {
    private double min;
    private double max;
    public DropoutParameter(double min, double max) {
        super(new UniformDoubleDistribution(min,max));
        this.min=min;
        this.max=max;
    }

    @Override
    protected HyperParameter<Double> createNew(Double val) {
        DropoutParameter param = new DropoutParameter(min,max);
        param.set(val);
        return param;
    }

    @Override
    public NeuralNetConfiguration.Builder applyToNetwork(NeuralNetConfiguration.Builder networkBuilder) {
        return networkBuilder.dropOut(get());
    }

    @Override
    public Layer.Builder applyToLayer(Layer.Builder layerBuilder) {
        return layerBuilder.dropOut(get());
    }
}
