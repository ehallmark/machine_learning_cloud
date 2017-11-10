package data_pipeline.optimize.parameters;

import data_pipeline.optimize.distributions.ParameterDistribution;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ehallmark on 11/9/17.
 */
public class HyperParameter<T> {
    protected static final Random rand = new Random(569);
    protected AtomicReference<T> value;
    protected ParameterDistribution<T> distribution;

    protected HyperParameter(T initialVal, ParameterDistribution<T> distribution) {
        this.value = new AtomicReference<T>(initialVal);
        this.distribution=distribution;
    }

    protected HyperParameter(ParameterDistribution<T> distribution) {
        this(distribution.nextSample(),distribution);
    }

    public T get() {
        return value.get();
    }

    public HyperParameter<T> mutate() {
        return new HyperParameter<>(distribution.nextSample(),distribution);
    }

    public HyperParameter<T> crossover(HyperParameter<T> partner) {
        T val = rand.nextBoolean() ? get() : partner.get();
        return new HyperParameter<>(val,distribution);
    }
}
