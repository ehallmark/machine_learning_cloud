package data_pipeline.optimize.distributions;

import org.nd4j.linalg.api.rng.distribution.Distribution;

import java.util.Random;

/**
 * Created by ehallmark on 11/10/17.
 */
public class UniformIntegerDistribution implements ParameterDistribution<Integer> {
    private int min;
    private int max;
    private Random rand;
    public UniformIntegerDistribution(int min, int max) {
        this.min=min;
        this.rand = new Random(System.currentTimeMillis());
        this.max=max;
        if(max<=min) throw new RuntimeException("Illegal boundary... min="+min+", max="+max);
    }
    @Override
    public Integer nextSample() {
        return min + (rand.nextInt(max-min));
    }
}
