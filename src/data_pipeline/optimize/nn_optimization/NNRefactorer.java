package data_pipeline.optimize.nn_optimization;

import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Created by ehallmark on 11/16/17.
 */
public class NNRefactorer {
    public static MultiLayerNetwork updateNetworkLearningRate(MultiLayerNetwork orig, double learningRate, boolean dup) {
        Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier = builder -> builder.learningRate(learningRate).biasLearningRate(learningRate);
        Function<Layer,Void> layerApplier = layer -> {
           // layer.setLearningRate(learningRate);
           // layer.setBiasLearningRate(learningRate);
            return null;
        };
        return updateNetwork(orig,netApplier,layerApplier,false,true,dup);
    }

    public static MultiLayerNetwork updatePretrainAndBackprop(MultiLayerNetwork orig, boolean pretrain, boolean backprop, boolean dup) {
        Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier = builder -> builder;
        Function<Layer,Void> layerApplier = layer -> {
            return null;
        };
        return updateNetwork(orig,netApplier,layerApplier,pretrain,backprop,dup);
    }
    public static MultiLayerNetwork updateNetworkUpdater(MultiLayerNetwork orig, Updater updater, boolean dup) {
        Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier = builder -> builder.updater(updater);
        Function<Layer,Void> layerApplier = layer -> {
            //layer.setUpdater(updater);
            return null;
        };
        return updateNetwork(orig,netApplier,layerApplier,false,true,dup);
    }

    public static MultiLayerNetwork updateNetworkRegularization(MultiLayerNetwork orig, boolean regularize, double l2, boolean dup) {
        Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier = builder -> builder.regularization(regularize).l2(l2);
        Function<Layer,Void> layerApplier = layer -> {
            //layer.setL2(l2);
            return null;
        };
        return updateNetwork(orig,netApplier,layerApplier,false,true,dup);
    }

    public static MultiLayerNetwork updateNetwork(MultiLayerNetwork orig, Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier, Function<Layer,Void> layerApplier, boolean pretrain, boolean backprop, boolean duplicateParameters) {
        INDArray params = orig.params();
        NeuralNetConfiguration.ListBuilder conf = netApplier.apply(new NeuralNetConfiguration.Builder(orig.getDefaultConfiguration().clone()))
                .list();
        List<NeuralNetConfiguration> confs = new ArrayList<>(orig.getnLayers());
        for(int i = 0; i < orig.getnLayers(); i++) {
            NeuralNetConfiguration layerConf = orig.getLayerWiseConfigurations().getConf(i).clone();
            Layer layer = layerConf.getLayer();
            layerApplier.apply(layer);

            conf = conf.layer(i, layer);
            NeuralNetConfiguration layerConfBuilder = netApplier.apply(new NeuralNetConfiguration.Builder(layerConf))
                    .build();

            layerConfBuilder.setLayer(layer);

            confs.add(layerConfBuilder);
        }
        conf.setConfs(confs);

        MultiLayerNetwork net = new MultiLayerNetwork(
                conf.backprop(backprop).pretrain(pretrain).build()
        );
        net.init(params,duplicateParameters);
        return net;
    }
}
