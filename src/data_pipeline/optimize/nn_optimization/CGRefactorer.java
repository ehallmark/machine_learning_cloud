package data_pipeline.optimize.nn_optimization;

import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.InputPreProcessor;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.function.Function;

/**
 * Created by ehallmark on 11/16/17.
 */
public class CGRefactorer {
    public static ComputationGraph mergeParamsInBetween(ComputationGraph orig, ComputationGraphConfiguration newConf) {
        INDArray origParams = orig.params();
        INDArray newParams;
        org.deeplearning4j.nn.api.Layer[] oldLayers = orig.getLayers();
        org.deeplearning4j.nn.api.Layer[] newLayers;
        {
            ComputationGraph tmp = new ComputationGraph(newConf);
            tmp.init();
            newParams = tmp.params();
            newLayers = tmp.getLayers();
        }


        for(int i = 0; i < oldLayers.length; i++) {
            org.deeplearning4j.nn.api.Layer layer = oldLayers[i];
            int n = layer.numParams();
        }

        int l1 = origParams.length();
        int l2 = newParams.length();

        int remainder = (l2-l1)/2;

        newParams.get(NDArrayIndex.interval(remainder,l2-remainder)).assign(origParams);

        ComputationGraph newGraph = new ComputationGraph(newConf);
        newGraph.init(newParams,false);

        return newGraph;
    }


    public static ComputationGraph updateNetworkLearningRate(ComputationGraph orig, double learningRate, boolean dup) {
        Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier = builder -> builder.learningRate(learningRate).biasLearningRate(learningRate);
        Function<ComputationGraphConfiguration.GraphBuilder,ComputationGraphConfiguration.GraphBuilder> cgApplier = builder -> builder;
        Function<Layer,Void> layerApplier = layer -> {
            layer.setLearningRate(learningRate);
            layer.setBiasLearningRate(learningRate);
            return null;
        };
        return updateNetwork(orig,cgApplier,netApplier,layerApplier,false,true,dup);
    }

    public static ComputationGraph setInputPreprocessor(ComputationGraph orig, InputPreProcessor preProcessor, int layerIdx,  InputPreProcessor preProcessor2, int layerIdx2, boolean dup) {
        Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier = builder -> builder;
        Function<ComputationGraphConfiguration.GraphBuilder,ComputationGraphConfiguration.GraphBuilder> cgApplier = builder -> builder;
        Function<Layer,Void> layerApplier = layer -> {
            return null;
        };
        return updateNetwork(orig,cgApplier,netApplier,layerApplier,false,true,dup,preProcessor,layerIdx,preProcessor2,layerIdx2);
    }

    public static ComputationGraph updatePretrainAndBackprop(ComputationGraph orig, boolean pretrain, boolean backprop, boolean dup) {
        Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier = builder -> builder;
        Function<ComputationGraphConfiguration.GraphBuilder,ComputationGraphConfiguration.GraphBuilder> cgApplier = builder -> builder;
        Function<Layer,Void> layerApplier = layer -> {
            return null;
        };
        return updateNetwork(orig,cgApplier,netApplier,layerApplier,pretrain,backprop,dup);
    }
    public static ComputationGraph updateNetworkUpdater(ComputationGraph orig, Updater updater, boolean dup) {
        Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier = builder -> builder.updater(updater);
        Function<ComputationGraphConfiguration.GraphBuilder,ComputationGraphConfiguration.GraphBuilder> cgApplier = builder -> builder;
        Function<Layer,Void> layerApplier = layer -> {
            layer.setUpdater(updater);
            return null;
        };
        return updateNetwork(orig,cgApplier,netApplier,layerApplier,false,true,dup);
    }

    public static ComputationGraph updateNetworkRegularization(ComputationGraph orig, boolean regularize, double l2, boolean dup) {
        Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier = builder -> builder.regularization(regularize).l2(l2);
        Function<ComputationGraphConfiguration.GraphBuilder,ComputationGraphConfiguration.GraphBuilder> cgApplier = builder -> builder;
        Function<Layer,Void> layerApplier = layer -> {
            layer.setL2(l2);
            return null;
        };
        return updateNetwork(orig,cgApplier,netApplier,layerApplier,false,true,dup);
    }

    public static ComputationGraph updateNetwork(ComputationGraph orig, Function<ComputationGraphConfiguration.GraphBuilder,ComputationGraphConfiguration.GraphBuilder> cgApplier, Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier, Function<Layer,Void> layerApplier, boolean pretrain, boolean backprop, boolean duplicateParameters) {
        return updateNetwork(orig,cgApplier,netApplier,layerApplier,pretrain,backprop,duplicateParameters,null,-1,null,-1);
    }

    public static ComputationGraph updateNetwork(ComputationGraph orig, Function<ComputationGraphConfiguration.GraphBuilder,ComputationGraphConfiguration.GraphBuilder> cgApplier, Function<NeuralNetConfiguration.Builder,NeuralNetConfiguration.Builder> netApplier, Function<Layer,Void> layerApplier, boolean pretrain, boolean backprop, boolean duplicateParameters, InputPreProcessor inputPreProcessor, int layerIdx, InputPreProcessor inputPreProcessor2, int layerIdx2) {
        INDArray params = orig.params();
        ComputationGraphConfiguration configClone = orig.getConfiguration().clone();
        NeuralNetConfiguration.Builder netConfigClone = netApplier.apply(new NeuralNetConfiguration.Builder(configClone.getDefaultConfiguration().clone()));
        configClone.setDefaultConfiguration(netConfigClone.build());
        ComputationGraphConfiguration.GraphBuilder conf = cgApplier.apply(new ComputationGraphConfiguration.GraphBuilder(
                configClone,
                netConfigClone
        ));

        for(int i = 0; i < orig.getLayers().length; i++) {
            NeuralNetConfiguration layerConf = orig.getLayer(i).conf().clone();
            Layer layer = layerConf.getLayer();
            layerApplier.apply(layer);
            if(inputPreProcessor!=null&&layerIdx==i) {
                conf = conf.addLayer(layer.getLayerName(), layer, inputPreProcessor, configClone.getVertexInputs().get(layer.getLayerName()).toArray(new String[]{}));
            } else if(inputPreProcessor2!=null&&layerIdx2==i) {
                conf = conf.addLayer(layer.getLayerName(), layer, inputPreProcessor2, configClone.getVertexInputs().get(layer.getLayerName()).toArray(new String[]{}));
            } else {
                conf = conf.addLayer(layer.getLayerName(), layer, configClone.getVertexInputs().get(layer.getLayerName()).toArray(new String[]{}));
            }

        }

        ComputationGraph net = new ComputationGraph(
                conf.backprop(backprop).pretrain(pretrain).build()
        );
        net.init(params,duplicateParameters);
        return net;
    }

    public static void main(String[] args) {
        // test
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .learningRate(0.01)
                .graphBuilder()
                .addInputs("input")
                .addLayer("L1", new DenseLayer.Builder().nIn(3).nOut(4).build(), "input")
                .addLayer("out1", new OutputLayer.Builder()
                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .nIn(4).nOut(3).build(), "L1")
                .addLayer("out2", new OutputLayer.Builder()
                        .lossFunction(LossFunctions.LossFunction.MSE)
                        .nIn(4).nOut(2).build(), "L1")
                .setOutputs("out1","out2")
                .build();

        ComputationGraph graph = new ComputationGraph(conf);
        graph.init();

        System.out.println("New config: "+graph.getConfiguration().toYaml());

        graph = updateNetworkLearningRate(graph,0.0001,false);

        System.out.println("New config: "+graph.getConfiguration().toYaml());
    }
}
