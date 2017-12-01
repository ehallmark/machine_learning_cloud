package data_pipeline.optimize.parameters.distributions;

import java.util.Random;

/**
 * Created by ehallmark on 11/10/17.
 */
public class UniformDoubleDistribution implements ParameterDistribution<Double> {
    private double min;
    private double max;
    private Random rand;
    public UniformDoubleDistribution(double min, double max) {
        this.min=min;
        this.rand = new Random(System.currentTimeMillis());
        this.max=max;
        if(max<min) throw new RuntimeException("Illegal boundary... min="+min+", max="+max);
    }
    @Override
    public Double nextSample() {
        return min + (rand.nextDouble()*(max-min));
    }
}
