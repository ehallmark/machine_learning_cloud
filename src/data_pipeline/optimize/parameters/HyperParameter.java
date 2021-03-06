package data_pipeline.optimize.parameters;

import data_pipeline.optimize.parameters.distributions.ParameterDistribution;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.graph.GraphVertex;
import org.deeplearning4j.nn.conf.layers.Layer;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ehallmark on 11/9/17.
 */
public abstract class HyperParameter<T> {
    protected static final Random rand = new Random(569);
    protected AtomicReference<T> value;
    protected ParameterDistribution<T> distribution;

    protected HyperParameter(ParameterDistribution<T> distribution) {
        this.value = new AtomicReference<T>(null);
        this.distribution=distribution;
    }

    public void set(T val) { value.set(val); }

    public T get() {
        if(value.get()==null) value.set(distribution.nextSample());
        return value.get();
    }

    public HyperParameter<T> mutate() {
        return createNew(distribution.nextSample());
    }

    public HyperParameter<T> crossover(HyperParameter<T> partner) {
        T val = rand.nextBoolean() ? get() : partner.get();
        return createNew(val);
    }

    protected abstract HyperParameter<T> createNew(T val);

    public abstract NeuralNetConfiguration.Builder applyToNetwork(NeuralNetConfiguration.Builder networkBuilder);
    public abstract Layer.Builder applyToLayer(Layer.Builder layerBuilder);

}
