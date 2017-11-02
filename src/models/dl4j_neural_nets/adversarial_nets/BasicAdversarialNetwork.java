package models.dl4j_neural_nets.adversarial_nets;

import models.similarity_models.signatures.NDArrayHelper;
import org.apache.commons.math3.util.Pair;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.DefaultRandom;
import org.nd4j.linalg.api.rng.distribution.Distribution;
import org.nd4j.linalg.api.rng.distribution.impl.NormalDistribution;
import org.nd4j.linalg.api.rng.distribution.impl.UniformDistribution;
import org.nd4j.linalg.dataset.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.lossfunctions.LossFunctions;

/**
 * Created by Evan on 10/16/2017.
 */
public class BasicAdversarialNetwork {
    private static int numInputs = 30;
    public static void main(String[] args) {
        ComputationGraphConfiguration adversaryConf = new NeuralNetConfiguration.Builder()
                .learningRate(0.01)
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.TANH)
                .graphBuilder()
                .addInputs("Input")
                // add adversarial hidden layers
                .addLayer("Adversary", new OutputLayer.Builder()
                        .nIn(numInputs)
                        .nOut(numInputs)
                        .activation(Activation.SIGMOID)
                        .lossFunction(new AdversaryLossFunction())
                        .build(), "Input")
                .setOutputs("Adversary")
                .build();

        ComputationGraph adversary = new ComputationGraph(adversaryConf);
        adversary.init();

        ComputationGraphConfiguration generatorConf = new NeuralNetConfiguration.Builder()
                .learningRate(0.01)
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.TANH)
                .graphBuilder()
                .addInputs("Input")
                // add generative hidden layers
                .addLayer("Input2", new DenseLayer.Builder()
                        .nIn(numInputs)
                        .nOut(numInputs)
                        .build(), "Input")
                .addLayer("Generator", new OutputLayer.Builder()
                        .nIn(numInputs)
                        .nOut(numInputs)
                        .lossFunction(new GeneratorLossFunction(adversary))
                        .build(), "Input2")
                .setOutputs("Generator")
                .build();

        ComputationGraph generator = new ComputationGraph(generatorConf);
        generator.init();

        int numExamples = 100;
        Distribution noiseDist = new NormalDistribution(0,1);
        Distribution actualDist = new UniformDistribution(-1,1);
        INDArray generatorFeatures = Nd4j.create(numExamples,numInputs);
        INDArray adversaryLabels = Nd4j.create(numExamples,numInputs);
        for(int i = 0; i < numExamples; i++) {
            if(i%2==0) {
                // random noise
                generatorFeatures.putRow(i, Nd4j.rand(new int[]{numInputs}, noiseDist));
                adversaryLabels.putRow(i, Nd4j.zeros(numInputs));
            } else {
                generatorFeatures.putRow(i, Nd4j.rand(new int[]{numInputs}, actualDist));
                adversaryLabels.putRow(i, Nd4j.ones(numInputs));
            }
        }
        MultiDataSet generatorDataSet = new MultiDataSet(new INDArray[]{generatorFeatures},new INDArray[]{generatorFeatures});
        MultiDataSet adversaryDataSet = new MultiDataSet(new INDArray[]{generatorFeatures},new INDArray[]{adversaryLabels});
        for(int i = 0; i < 10000; i++) {
            // adversary dataSet
            adversaryDataSet.setFeatures(generator.output(false,generatorFeatures));
            adversary.fit(adversaryDataSet);

            System.out.println("Starting iteration: "+i);
            generator.fit(generatorDataSet);

            double aScore = adversaryLabels.distance1(adversary.output(false,generatorFeatures)[0])/adversaryLabels.rows();
            System.out.println("A Score: "+aScore);
            double gScore = NDArrayHelper.sumOfCosineSimByRow(generatorFeatures,generator.output(false,generatorFeatures)[0]);
            System.out.println("G Score: "+gScore);
        }
    }
}
