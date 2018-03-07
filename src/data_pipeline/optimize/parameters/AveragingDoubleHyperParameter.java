package data_pipeline.optimize.parameters;

import data_pipeline.optimize.parameters.distributions.ParameterDistribution;

/**
 * Created by ehallmark on 11/9/17.
 */
public abstract class AveragingDoubleHyperParameter extends HyperParameter<Double> {

    protected AveragingDoubleHyperParameter(ParameterDistribution<Double> distribution) {
        super(distribution);
    }

    @Override
    public HyperParameter<Double> crossover(HyperParameter<Double> partner) {
        double r = rand.nextDouble();
        double val = (r * get()) + ((1d - r) * partner.get());
        return createNew(val);
    }
}
