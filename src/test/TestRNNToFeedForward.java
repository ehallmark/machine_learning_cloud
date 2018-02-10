package test;

import models.similarity_models.combined_similarity_model.DeepCPC2VecEncodingModel;
import org.deeplearning4j.iterator.CnnSentenceDataSetIterator;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.graph.L2NormalizeVertex;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.graph.PreprocessorVertex;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToRnnPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.RnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TestRNNToFeedForward {
    public static void main(String[] args) {
        int maxSample = 7;
        int vectorSize = 4;
        int hiddenLayerSize1 = 17;
        int hiddenLayerSize2 = 13;


        //Basic configuration

        PoolingType globalPoolingType = PoolingType.MAX;

        //Set up the network configuration. Note that we have multiple convolution layers, each wih filter
        //widths of 3, 4 and 5 as per Kim (2014) paper.

        Nd4j.getMemoryManager().setAutoGcWindow(5000);

        ComputationGraphConfiguration config = new NeuralNetConfiguration.Builder()
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.TANH)
                .updater(Updater.RMSPROP)
                .convolutionMode(ConvolutionMode.Same)      //This is important so we can 'stack' the results later
                .regularization(true).l2(0.0001)
                .learningRate(0.01)
                .graphBuilder()
                .addInputs("input")
                .addVertex("n1", new L2NormalizeVertex(), "input")
                .addLayer("l1", new DenseLayer.Builder().nIn(vectorSize*maxSample).nOut(vectorSize*maxSample).build(),"n1")
                .addVertex("rl1", new ReshapeVertex(-1,1,vectorSize,maxSample),"l1")
                .addLayer("c1", new ConvolutionLayer.Builder()
                        .kernelSize(vectorSize,1)
                        .stride(vectorSize,1)
                        .nIn(1)
                        .nOut(hiddenLayerSize1)
                        .build(), "rl1")
                .addLayer("c2", new ConvolutionLayer.Builder()
                        .kernelSize(vectorSize,2)
                        .stride(vectorSize,1)
                        .nIn(1)
                        .nOut(hiddenLayerSize1)
                        .build(), "rl1")
                .addLayer("c3", new ConvolutionLayer.Builder()
                        .kernelSize(vectorSize,3)
                        .stride(vectorSize,1)
                        .nIn(1)
                        .nOut(hiddenLayerSize1)
                        .build(), "rl1")
                .addLayer("c4", new ConvolutionLayer.Builder()
                        .kernelSize(vectorSize,4)
                        .stride(vectorSize,1)
                        .nIn(1)
                        .nOut(hiddenLayerSize1)
                        .build(), "rl1")
                .addVertex("m1", new MergeVertex(), "c1", "c2", "c3", "c4")      //Perform depth concatenation
                .addLayer("p1", new GlobalPoolingLayer.Builder()
                        .poolingType(globalPoolingType)
                        .dropOut(0.5)
                        .build(), "m1")
                .addLayer("o1", new DenseLayer.Builder()
                        .nIn(hiddenLayerSize1*4)
                        .nOut(hiddenLayerSize2)
                        .build(), "p1")
                .addLayer("v1", new DenseLayer.Builder()
                        .nIn(hiddenLayerSize2)
                        .nOut(vectorSize)
                        .build(), "o1")
                .addLayer("o2", new DenseLayer.Builder()
                        .nIn(vectorSize)
                        .nOut(hiddenLayerSize2)
                        .build(), "v1")
                .addLayer("o3", new DenseLayer.Builder()
                        .nIn(hiddenLayerSize2)
                        .nOut(hiddenLayerSize1)
                        .build(), "o2")
                .addLayer("output", new OutputLayer.Builder()
                        .lossFunction(LossFunctions.LossFunction.COSINE_PROXIMITY)
                        .activation(Activation.TANH)
                        .nIn(hiddenLayerSize1)
                        .nOut(vectorSize*maxSample)
                        .build(), "o3")
                .setOutputs("output")
                .build();

        ComputationGraph graph = new ComputationGraph(config);
        graph.init();

        for(int i = 0; i < graph.getLayers().length; i++) {
            System.out.println("Layer "+i+": "+String.join("\n\t",graph.getLayer(i).paramTable().entrySet().stream().map(e->e.getKey()+": "+Arrays.toString(e.getValue().shape())).collect(Collectors.toList())));
        }


        INDArray[] data3 = new INDArray[]{Nd4j.randn(new int[]{3,vectorSize*maxSample})};
        INDArray[] data5 = new INDArray[]{Nd4j.randn(new int[]{5,vectorSize*maxSample})};


        for(int i = 0; i < 1000; i++) {
            graph.fit(data3,data3);
            graph.fit(data5,data5);
            System.out.println("Score "+i+": "+graph.score());
        }

        for(int i = 1; i <= graph.getNumLayers(); i++) {
            try {

               // graph.setLayerMaskArrays(mask3,mask3);
                System.out.println("Shape of " + i + ": " + Arrays.toString(DeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(i),data3).shape()));
               // graph.clearLayerMaskArrays();
               // graph.setLayerMaskArrays(mask5,mask5);
                System.out.println("Shape of " + i + ": " + Arrays.toString(DeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(i),data5).shape()));
               // graph.clearLayerMaskArrays();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
