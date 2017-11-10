package data_pipeline.optimize.distributions;

import org.nd4j.linalg.api.rng.distribution.Distribution;

/**
 * Created by ehallmark on 11/10/17.
 */
public class NumericDistribution implements ParameterDistribution<Double> {
    private Distribution distribution;
    public NumericDistribution(Distribution distribution) {
        this.distribution=distribution;
    }
    @Override
    public Double nextSample() {
        return distribution.sample();
    }
}
