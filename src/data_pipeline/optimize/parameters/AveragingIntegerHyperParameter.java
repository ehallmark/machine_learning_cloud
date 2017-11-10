package data_pipeline.optimize.parameters;

import data_pipeline.optimize.distributions.ParameterDistribution;

/**
 * Created by ehallmark on 11/9/17.
 */
public class AveragingIntegerHyperParameter extends HyperParameter<Integer> {
    protected AveragingIntegerHyperParameter(Integer initialVal, ParameterDistribution<Integer> distribution) {
        super(initialVal,distribution);
    }
    protected AveragingIntegerHyperParameter(ParameterDistribution<Integer> distribution) {
        this(distribution.nextSample(),distribution);
    }

    @Override
    public HyperParameter<Integer> mutate() {
        return new AveragingIntegerHyperParameter(distribution.nextSample(),distribution);
    }

    @Override
    public HyperParameter<Integer> crossover(HyperParameter<Integer> partner) {
        double r = rand.nextDouble();
        float val = (float) ((r * get()) + ((1d - r) * partner.get()));
        return new AveragingIntegerHyperParameter(Math.round(val),distribution);
    }
}
