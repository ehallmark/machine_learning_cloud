package data_pipeline.optimize.parameters;

import data_pipeline.optimize.parameters.distributions.ParameterDistribution;

/**
 * Created by ehallmark on 11/9/17.
 */
public abstract class AveragingIntegerHyperParameter extends HyperParameter<Integer> {

    protected AveragingIntegerHyperParameter(ParameterDistribution<Integer> distribution) {
        super(distribution);
    }

    @Override
    public HyperParameter<Integer> crossover(HyperParameter<Integer> partner) {
        double r = rand.nextDouble();
        float val = (float) ((r * get()) + ((1d - r) * partner.get()));
        return createNew(Math.round(val));
    }

}
