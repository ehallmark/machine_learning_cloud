package data_pipeline.optimize.parameters.distributions;

/**
 * Created by ehallmark on 11/10/17.
 */
public interface ParameterDistribution<T> {
    T nextSample();
}
