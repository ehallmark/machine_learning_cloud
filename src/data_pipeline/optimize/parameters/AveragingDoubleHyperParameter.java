package data_pipeline.optimize.parameters;

import data_pipeline.optimize.distributions.ParameterDistribution;

/**
 * Created by ehallmark on 11/9/17.
 */
public abstract class AveragingDoubleHyperParameter extends HyperParameter<Double> {
    protected AveragingDoubleHyperParameter(Double initialVal, ParameterDistribution<Double> distribution) {
        super(initialVal,distribution);
    }
    protected AveragingDoubleHyperParameter(ParameterDistribution<Double> distribution) {
        this(distribution.nextSample(),distribution);
    }

    @Override
    public HyperParameter<Double> mutate() {
        return createNew(distribution.nextSample());
    }

    @Override
    public HyperParameter<Double> crossover(HyperParameter<Double> partner) {
        double r = rand.nextDouble();
        double val = (r * get()) + ((1d - r) * partner.get());
        return createNew(val);
    }
}
