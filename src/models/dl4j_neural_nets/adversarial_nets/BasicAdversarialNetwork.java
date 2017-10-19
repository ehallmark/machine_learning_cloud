package models.dl4j_neural_nets.adversarial_nets;

import org.apache.commons.math3.util.Pair;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.DefaultRandom;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.ILossFunction;

/**
 * Created by Evan on 10/16/2017.
 */
public class BasicAdversarialNetwork {
    private static int batchSize = 10;
    private static int numInputs = 30;
    private static int numOutputs = 10;
    public static void main(String[] args) {
        MultiLayerConfiguration generatorConf = new NeuralNetConfiguration.Builder()
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(numInputs)
                        .nOut(numInputs/2)
                        .activation(Activation.RELU)
                        .build()
                )
                .layer(1, new OutputLayer.Builder()
                        .nIn(numInputs/2)
                        .nOut(numInputs)
                        .build()

                )
                .layer(2, new OutputLayer.Builder()
                        .nIn(numInputs)
                        .nOut(2)
                        .build()
                )
                .build();

        MultiLayerConfiguration adversaryConf = new NeuralNetConfiguration.Builder()
                .list()
                .layer(0, new DenseLayer.Builder()
                        .activation(Activation.RELU)
                        .nIn(numInputs)
                        .nOut(numInputs/2)
                        .build()
                )
                .layer(1, new OutputLayer.Builder()
                        .nIn(numInputs/2)
                        .nOut(2)
                        .build()
                )
                .build();


        MultiLayerNetwork generator = new MultiLayerNetwork(generatorConf);
        MultiLayerNetwork adversary = new MultiLayerNetwork(adversaryConf);

        // init
        generator.init();
        adversary.init();

        INDArray actual = Nd4j.rand(batchSize,numInputs);

        for(int i = 0; i < 1000; i++) {
            INDArray toFeed = generator.activateSelectedLayers(0,1,fakeData());

            adversary.fit(actual, Nd4j.hstack(Nd4j.zeros(batchSize,1),Nd4j.ones(batchSize,1)));
            adversary.fit(toFeed, Nd4j.hstack(Nd4j.ones(batchSize,1),Nd4j.zeros(batchSize,1)));

            generator.fit(toFeed, createLabels(adversary.activateSelectedLayers(0,1,toFeed)));
            generator.fit(actual, createLabels(adversary.activateSelectedLayers(0,1,actual)));

            System.out.println("Finished epoch: "+i);
            System.out.println("Score Adversary: "+adversary.score());
            System.out.println("Score Generator: "+generator.score());
        }
    }

    private static INDArray createLabels(INDArray array) {
        INDArray newArray = array.dup();
        INDArray min = array.min(1);
        INDArray max = array.max(1).sub(min);
        newArray.getColumn(0).subi(min).divi(max);
        newArray.getColumn(1).subi(min).divi(max);
        return newArray;
    }

    private static INDArray fakeData() {
        return Nd4j.randn(batchSize,numInputs);
    }

}
