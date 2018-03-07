package data_pipeline.optimize.parameters;

import data_pipeline.optimize.parameters.distributions.DiscreteDistribution;
import data_pipeline.optimize.parameters.distributions.ParameterDistribution;

import java.util.Arrays;
import java.util.List;

/**
 * Created by ehallmark on 11/9/17.
 */
public abstract class SimpleDiscreteHyperParameter<T> extends HyperParameter<T> {
    protected List<T> values;
    protected SimpleDiscreteHyperParameter(List<T>  values) {
        super(new DiscreteDistribution<>(values));
        this.values=values;
    }
}
