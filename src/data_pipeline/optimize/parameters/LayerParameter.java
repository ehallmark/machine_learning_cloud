package data_pipeline.optimize.parameters;

import data_pipeline.optimize.distributions.ParameterDistribution;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.Layer;

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ehallmark on 11/9/17.
 */
public abstract class LayerParameter<T> extends HyperParameter<T> {

    protected LayerParameter(ParameterDistribution<T> distribution) {
        super(distribution);
    }

    public abstract Layer.Builder applyToLayer(Layer.Builder networkBuilder);

}
