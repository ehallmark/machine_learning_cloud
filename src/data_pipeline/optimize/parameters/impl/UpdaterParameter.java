package data_pipeline.optimize.parameters.impl;

import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.SimpleDiscreteHyperParameter;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.List;

/**
 * Created by ehallmark on 11/10/17.
 */
public class UpdaterParameter extends SimpleDiscreteHyperParameter<Updater> {
    public UpdaterParameter(List<Updater> functions) {
        super(functions);
    }

    @Override
    protected HyperParameter<Updater> createNew(Updater val) {
        UpdaterParameter param = new UpdaterParameter(values);
        param.set(val);
        return param;
    }

    @Override
    public Layer.Builder applyToLayer(Layer.Builder layerBuilder) {
        return layerBuilder.updater(get());
    }

    @Override
    public NeuralNetConfiguration.Builder applyToNetwork(NeuralNetConfiguration.Builder networkBuilder) {
        return networkBuilder.updater(get());
    }
}
