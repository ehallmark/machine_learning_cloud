package test;

import models.similarity_models.combined_similarity_model.DeepCPC2VecEncodingModel;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.graph.PreprocessorVertex;
import org.deeplearning4j.nn.conf.layers.Convolution1DLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.GravesBidirectionalLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.conf.preprocessor.FeedForwardToRnnPreProcessor;
import org.deeplearning4j.nn.conf.preprocessor.RnnToFeedForwardPreProcessor;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.Arrays;

public class TestRNNToFeedForward {
    public static void main(String[] args) {
        int maxSample = 16;
        int hiddenLayerSize = 10;
        int hiddenLayerSize2 = 9;
        int numFeatures = 3;
        int linearTotal = maxSample * hiddenLayerSize;


        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .activation(Activation.TANH)
                .learningRate(0.0001)
                .graphBuilder()
                .addInputs("x")
                .addLayer("1", new GravesBidirectionalLSTM.Builder().nIn(numFeatures).nOut(hiddenLayerSize).build(), "x")
                .addLayer("2", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(), "1")
                //.addVertex("v1", new PreprocessorVertex(new RnnToFeedForwardPreProcessor()), "2")
                .addVertex("v2", new ReshapeVertex(-1,linearTotal), "2")
                .addLayer("3", new DenseLayer.Builder().nIn(linearTotal).nOut(hiddenLayerSize2).build(), "v2")
                .addVertex("c1", new ReshapeVertex(-1,1,hiddenLayerSize2,4), "3")
                .addLayer("4", new Convolution1DLayer.Builder().nIn(hiddenLayerSize2).nOut(4).build(), "c1")
                .addLayer("5", new DenseLayer.Builder().nIn(4).nOut(hiddenLayerSize2).build(), "4")
                .addLayer("6", new DenseLayer.Builder().nIn(hiddenLayerSize2).nOut(linearTotal).build(), "5")
                .addVertex("v3", new ReshapeVertex(-1,hiddenLayerSize,maxSample), "6")
                //.addVertex("v4", new PreprocessorVertex(new FeedForwardToRnnPreProcessor()), "v3")
                .addLayer("7", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(), "v3")
                .addLayer("8", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(), "7")
                .addLayer("y", new RnnOutputLayer.Builder().nIn(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.MSE).nOut(numFeatures).build(), "8")
                .setOutputs("y")
                .build();

        ComputationGraph graph = new ComputationGraph(conf);
        graph.init();

       // for(int i = 0; i < 1000; i++) {
       //     graph.fit(new INDArray[]{data3}, new INDArray[]{data3});
       //     graph.fit(new INDArray[]{data5}, new INDArray[]{data5});
       //     System.out.println("Score "+i+": "+graph.score());
       // }

        for(int i = 1; i <= graph.getNumLayers(); i++) {
            try {

                INDArray[] data3 = new INDArray[]{Nd4j.randn(new int[]{3,numFeatures,maxSample})};
                INDArray[] mask3 = new INDArray[]{Nd4j.randn(new int[]{3,maxSample})};
                INDArray[] data5 = new INDArray[]{Nd4j.randn(new int[]{5,numFeatures,maxSample})};
                INDArray[] mask5 = new INDArray[]{Nd4j.randn(new int[]{5,maxSample})};

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
