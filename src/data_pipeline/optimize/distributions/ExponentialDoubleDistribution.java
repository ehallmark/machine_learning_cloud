package data_pipeline.optimize.distributions;

import com.google.common.util.concurrent.AtomicDouble;
import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.nd4j.linalg.api.rng.distribution.Distribution;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 11/10/17.
 */
public class ExponentialDoubleDistribution implements ParameterDistribution<Double> {
    private static final Random rand = new Random(34636);
    private double start;
    private double end;
    private boolean reverse;
    private ExponentialDistribution distribution;
    public ExponentialDoubleDistribution(double start, double end, boolean reverse) {
        this.start=start;
        this.end=end;
        this.reverse=reverse;
        this.distribution=new ExponentialDistribution(1d);
    }
    @Override
    public Double nextSample() {
        double sample = (1d-distribution.cumulativeProbability(rand.nextDouble()*5d)) * (end-start);
        if(reverse) {
            return end-sample;
        } else {
            return start+sample;
        }
    }


    public static void main(String[] args) {
        // testing
        AtomicDouble total = new AtomicDouble(0d);
        AtomicInteger cnt = new AtomicInteger(0);
        ExponentialDoubleDistribution distribution = new ExponentialDoubleDistribution(0.000001,1,true);
        for( int i = 0; i < 1000; i ++) {
            double sample = distribution.nextSample();
            total.getAndAdd(sample);
            cnt.getAndIncrement();
            System.out.println("sample: "+sample);
        }
        System.out.println("Avg: "+total.get()/cnt.get());
    }
}
