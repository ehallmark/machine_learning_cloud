package test;

import models.similarity_models.combined_similarity_model.DeepCPC2VecEncodingModel;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.graph.PreprocessorVertex;
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
        int numFeatures = 3;
        int linearTotal = maxSample * hiddenLayerSize;

        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .activation(Activation.TANH)
                .learningRate(0.0001)
                .graphBuilder()
                .addInputs("x")
                .addLayer("1", new GravesBidirectionalLSTM.Builder().nIn(numFeatures).nOut(hiddenLayerSize).build(), "x")
                .addLayer("2", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(), "1")
                .addVertex("v1", new PreprocessorVertex(new RnnToFeedForwardPreProcessor()), "2")
                .addVertex("v2", new ReshapeVertex(-1,linearTotal), "v1")
                .addLayer("3", new DenseLayer.Builder().nIn(linearTotal).nOut(8).build(), "v2")
                .addLayer("4", new DenseLayer.Builder().nIn(8).nOut(4).build(), "3")
                .addLayer("5", new DenseLayer.Builder().nIn(4).nOut(8).build(), "4")
                .addLayer("6", new DenseLayer.Builder().nIn(8).nOut(linearTotal).build(), "5")
                .addVertex("v3", new ReshapeVertex(-1,hiddenLayerSize), "6")
                .addVertex("v4", new PreprocessorVertex(new FeedForwardToRnnPreProcessor()), "v3")
                .addLayer("7", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(), "v4")
                .addLayer("8", new GravesBidirectionalLSTM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build(), "7")
                .addLayer("y", new RnnOutputLayer.Builder().nIn(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.MSE).nOut(numFeatures).build(), "8")
                .setOutputs("y")
                .build();

        ComputationGraph graph = new ComputationGraph(conf);
        graph.init();

        INDArray data3 = Nd4j.randn(new int[]{3,numFeatures,maxSample});
        INDArray data5 = Nd4j.randn(new int[]{5,numFeatures,maxSample});

        for(int i = 0; i < 1000; i++) {
            graph.fit(new INDArray[]{data3}, new INDArray[]{data3});
            graph.fit(new INDArray[]{data5}, new INDArray[]{data5});
            System.out.println("Score "+i+": "+graph.score());
        }

        for(int i = 1; i <= 8; i++) {
            try {
                System.out.println("Shape of " + i + ": " + Arrays.toString(DeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(i),data3).shape()));
                System.out.println("Shape of " + i + ": " + Arrays.toString(DeepCPC2VecEncodingModel.feedForwardToVertex(graph, String.valueOf(i),data5).shape()));
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
