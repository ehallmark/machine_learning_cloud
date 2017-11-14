package data_pipeline.optimize.nn_optimization;

import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.LayerParameter;
import data_pipeline.optimize.parameters.impl.LearningRateParameter;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by ehallmark on 11/10/17.
 */
public class NNOptimizer {
    private List<MultiLayerNetwork> networks;
    private List<HyperParameter<?>> hyperParameters;

    protected static MultiLayerNetwork buildNetworkWithHyperParameters(
            NeuralNetConfiguration preModel, List<Layer.Builder> layerModels,
            List<HyperParameter> modelParameters, List<LayerParameter> layerParameters)
    {
        NeuralNetConfiguration.Builder newModelConf = new NeuralNetConfiguration.Builder(preModel);
        for (HyperParameter<?> hyperParameter : modelParameters) {
            newModelConf = hyperParameter.applyToNetwork(newModelConf);
        }
        NeuralNetConfiguration.ListBuilder layerBuilder = newModelConf.list();
        for(int i = 0; i < layerModels.size(); i++) {
            System.out.println("Updating layer "+(i+1)+" of "+layerModels.size());
            Layer.Builder<?> layer = layerModels.get(i);
            for (LayerParameter hyperParameter : layerParameters) {
                layer = hyperParameter.applyToLayer(layer);
            }
            layerBuilder = layerBuilder.layer(i, layer.build());
        }
        // swap out configs
        MultiLayerNetwork net = new MultiLayerNetwork(layerBuilder.build().clone());
        net.init();
        return net;
    }

    public static void main(String[] args) {
        // test
        MultiLayerNetwork net = buildNetworkWithHyperParameters(new NeuralNetConfiguration.Builder().learningRate(0.1).build(),
                Arrays.asList(new DenseLayer.Builder().nIn(20).nOut(25)),
                Arrays.asList(new LearningRateParameter(0.0001,0.1)),
                Collections.emptyList()
        );
        net.init();


    }
}
