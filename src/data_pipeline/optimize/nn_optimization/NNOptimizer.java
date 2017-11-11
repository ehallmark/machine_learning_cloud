package data_pipeline.optimize.nn_optimization;

import data_pipeline.optimize.parameters.HyperParameter;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ehallmark on 11/10/17.
 */
public class NNOptimizer {
    private List<MultiLayerNetwork> networks;
    private List<HyperParameter<?>> hyperParameters;

    protected MultiLayerNetwork buildNetworkWithHyperParameters(MultiLayerConfiguration initial, List<HyperParameter<?>> hyperParameters) {
        List<NeuralNetConfiguration> newConfs = new ArrayList<>();
        for(int i = 0; i < initial.getConfs().size(); i++) {
            System.out.println("Updating conf "+(i+1)+" of "+initial.getConfs().size());
            NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder(initial.getConf(i));
            for (HyperParameter<?> hyperParameter : hyperParameters) {
                builder = hyperParameter.applyToNetwork(builder);
            }
            newConfs.add(builder.build());
        }
        // swap out configs
        initial.setConfs(newConfs);
        MultiLayerNetwork net = new MultiLayerNetwork(initial.clone());
        net.init();
        return net;
    }
}
