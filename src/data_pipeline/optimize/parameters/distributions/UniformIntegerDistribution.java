package data_pipeline.optimize.parameters.distributions;

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
        if(max<min) throw new RuntimeException("Illegal boundary... min="+min+", max="+max);
    }
    @Override
    public Integer nextSample() {
        if(min==max) return min;
        return min + (rand.nextInt(max-min));
    }
}
