package models.similarity_models.signatures;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.Map;

/**
 * Created by Evan on 10/29/2017.
 */
public class WordToCPCNetwork {
    public static void main(String[] args) {
        // get vocab
        Map<String,Integer> vocabIdxMap = null;
        final int vocabSize = 100000;
        final int hiddenLayerSize = 512;
        final int outputSize = SignatureSimilarityModel.VECTOR_SIZE;

        final int seed = 69;
        Nd4j.getRandom().setSeed(seed);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .activation(Activation.LEAKYRELU)
                .updater(Updater.RMSPROP)
                .rmsDecay(0.95)
                .seed(seed)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(0.01)
                .miniBatch(true)
                .weightInit(WeightInit.XAVIER)
                .regularization(true).l2(1e-4)
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(vocabSize)
                        .nOut(hiddenLayerSize)
                        .build()
                ).layer(1, new DenseLayer.Builder()
                        .nIn(hiddenLayerSize)
                        .nOut(hiddenLayerSize)
                        .build()
                ).layer(2, new DenseLayer.Builder()
                        .nIn(hiddenLayerSize)
                        .nOut(hiddenLayerSize)
                        .build()
                ).layer(4, new OutputLayer.Builder()
                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .activation(Activation.TANH)
                        .nIn(hiddenLayerSize)
                        .nOut(outputSize)
                        .build()
                ).build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();



    }
}
