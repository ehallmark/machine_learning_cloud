package test;

import data_pipeline.optimize.nn_optimization.NNOptimizer;
import models.similarity_models.combined_similarity_model.AbstractEncodingModel;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.graph.L2NormalizeVertex;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import tools.ReshapeVertex;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TestRNNToFeedForward {
    public static void main(String[] args) {
        int maxSample = 6;
        int vectorSize = 32;
        int hiddenLayerSizeRNN = vectorSize * 6;
        int hiddenLayerSizeFF = vectorSize * 3;
        int input1 = 7;
        int input2 = 13;
        double learningRate = 0.001;
        int linearTotal = maxSample * hiddenLayerSizeRNN;

        //Basic configuration

        PoolingType globalPoolingType = PoolingType.MAX;

        //Set up the network configuration. Note that we have multiple convolution layers, each wih filter
        //widths of 3, 4 and 5 as per Kim (2014) paper.

        Nd4j.getMemoryManager().setAutoGcWindow(5000);


        Updater updater = Updater.RMSPROP;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        Activation activation = Activation.TANH;
        Activation outputActivation = Activation.TANH;
        ComputationGraphConfiguration config = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(learningRate)
                // .lrPolicyDecayRate(0.0001)
                // .lrPolicyPower(0.7)
                // .learningRateDecayPolicy(LearningRatePolicy.Inverse)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .activation(activation)
                .graphBuilder()
                .addInputs("x1","x2")
                .addVertex("1-0", new L2NormalizeVertex(), "x1")
                .addVertex("1-1", new L2NormalizeVertex(), "x2")
                .addLayer("2-0", new GravesBidirectionalLSTM.Builder().nIn(input1).nOut(hiddenLayerSizeRNN).build(), "1-0")
                .addLayer("2-1", new DenseLayer.Builder().nIn(input2).nOut(hiddenLayerSizeRNN).build(), "1-1")
                .addLayer("3-0", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "2-0")
                .addLayer("3-1", new DenseLayer.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "2-1")
                .addVertex("v2", new ReshapeVertex(-1,linearTotal), "3-0")
                .addLayer("4", new DenseLayer.Builder().nIn(linearTotal+hiddenLayerSizeRNN).nOut(linearTotal).build(), "v2","3-1")
                .addLayer("5", new DenseLayer.Builder().nIn(linearTotal).nOut(hiddenLayerSizeFF).build(), "4")
                .addLayer("6", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(hiddenLayerSizeFF).build(), "5")
                .addLayer("7", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(hiddenLayerSizeFF).build(), "6")
                .addLayer("v", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(vectorSize).build(), "7")
                .addLayer("8", new DenseLayer.Builder().nIn(vectorSize).nOut(hiddenLayerSizeFF).build(), "v")
                .addLayer("9", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(hiddenLayerSizeFF).build(), "8")
                .addLayer("10", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(hiddenLayerSizeFF).build(), "9")
                .addLayer("11", new DenseLayer.Builder().nIn(hiddenLayerSizeFF).nOut(linearTotal).build(), "10")
                .addLayer("12", new DenseLayer.Builder().nIn(linearTotal).nOut(linearTotal).build(), "11")
                .addVertex("v3", new ReshapeVertex(-1,hiddenLayerSizeRNN,maxSample), "12")
                .addLayer("13-0", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "v3")
                .addLayer("13-1", new DenseLayer.Builder().nIn(linearTotal).nOut(hiddenLayerSizeRNN).build(), "12")
                .addLayer("14-0", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "13-0")
                .addLayer("14-1", new DenseLayer.Builder().nIn(hiddenLayerSizeRNN).nOut(hiddenLayerSizeRNN).build(), "13-1")
                .addLayer("y1", new RnnOutputLayer.Builder().activation(outputActivation).nIn(hiddenLayerSizeRNN).lossFunction(lossFunction).nOut(input1).build(), "14-0")
                .addLayer("y2", new OutputLayer.Builder().activation(outputActivation).nIn(hiddenLayerSizeRNN).lossFunction(lossFunction).nOut(input2).build(), "14-1")
                .setOutputs("y1","y2")
                .backprop(true)
                .pretrain(false)
                .build();

        ComputationGraph graph = new ComputationGraph(config);
        graph.init();

        for(int i = 0; i < graph.getLayers().length; i++) {
            System.out.println("Layer "+i+": "+String.join("\n\t",graph.getLayer(i).paramTable().entrySet().stream().map(e->e.getKey()+": "+Arrays.toString(e.getValue().shape())).collect(Collectors.toList())));
        }


        INDArray[] data3 = new INDArray[]{Nd4j.randn(new int[]{3,input1,maxSample}), Nd4j.rand(3,input2)};
        INDArray[] data5 = new INDArray[]{Nd4j.randn(new int[]{5,input1,maxSample}), Nd4j.rand(5,input2)};


        for(int i = 0; i < 1000; i++) {
            graph.fit(data3,data3);
            graph.fit(data5,data5);
            System.out.println("Score "+i+": "+graph.score());
        }

        for(int i = 1; i <= graph.getNumLayers(); i++) {
            try {

               // graph.setLayerMaskArrays(mask3,mask3);
                System.out.println("Shape of " + i + ": " + Arrays.toString(AbstractEncodingModel.feedForwardToVertex(graph, String.valueOf(i),data3).shape()));
               // graph.clearLayerMaskArrays();
               // graph.setLayerMaskArrays(mask5,mask5);
                System.out.println("Shape of " + i + ": " + Arrays.toString(AbstractEncodingModel.feedForwardToVertex(graph, String.valueOf(i),data5).shape()));
               // graph.clearLayerMaskArrays();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
