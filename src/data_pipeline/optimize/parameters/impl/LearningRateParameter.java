package data_pipeline.optimize.parameters.impl;

import data_pipeline.optimize.distributions.ExponentialDoubleDistribution;
import data_pipeline.optimize.distributions.ParameterDistribution;
import data_pipeline.optimize.parameters.AveragingDoubleHyperParameter;
import data_pipeline.optimize.parameters.HyperParameter;
import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

/**
 * Created by ehallmark on 11/10/17.
 */
public class LearningRateParameter extends AveragingDoubleHyperParameter {
    private double min;
    private double max;
    public LearningRateParameter(Double val, double min, double max) {
        super(val, new ExponentialDoubleDistribution(min,max,false));
        this.min=min;
        this.max=max;
    }

    @Override
    protected HyperParameter<Double> createNew(Double val) {
        return new LearningRateParameter(val,min,max);
    }

    @Override
    public NeuralNetConfiguration.Builder applyToNetwork(NeuralNetConfiguration.Builder networkBuilder) {
        return networkBuilder.learningRate(get());
    }
}
