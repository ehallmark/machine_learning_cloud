package data_pipeline.optimize.distributions;

/**
 * Created by ehallmark on 11/10/17.
 */
public interface ParameterDistribution<T> {
    T nextSample();
}
