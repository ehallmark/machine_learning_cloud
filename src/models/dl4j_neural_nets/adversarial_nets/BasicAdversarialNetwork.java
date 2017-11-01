package models.dl4j_neural_nets.adversarial_nets;

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
import org.nd4j.linalg.dataset.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.lossfunctions.LossFunctions;

/**
 * Created by Evan on 10/16/2017.
 */
public class BasicAdversarialNetwork {
    private static int batchSize = 10;
    private static int numInputs = 30;
    private static int hiddenLayerSize = 20;
    public static void main(String[] args) {
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .learningRate(0.01)
                .weightInit(WeightInit.XAVIER)
                .activation(Activation.SIGMOID)
                .graphBuilder()
                .addInputs("Input")
                .addLayer("Adversary", new OutputLayer.Builder()
                        .nIn(numInputs)
                        .nOut(2)
                        .activation(Activation.SOFTMAX)
                        .lossFunction(new GeneratorLossFunction())
                        .build(), "Generator")
                .addLayer("Generator", new OutputLayer.Builder()
                        .nIn(numInputs)
                        .nOut(numInputs)
                        .activation(Activation.SIGMOID)
                        .lossFunction(new GeneratorLossFunction())
                        .build(), "Input")

                .setOutputs("Adversary","Generator")
                .build();

        ComputationGraph graph = new ComputationGraph(conf);
        graph.init();

        int numExamples = 100;
        Distribution noiseDist = new NormalDistribution(0,1);
        Distribution actualDist = new NormalDistribution(2,0.1);
        INDArray generatorFeatures = Nd4j.create(numExamples,numInputs);
        INDArray adversaryLabels = Nd4j.create(numExamples,2);
        for(int i = 0; i < numExamples; i++) {
            if(i%2==0) {
                // random noise
                generatorFeatures.putRow(i, Nd4j.rand(new int[]{numInputs}, noiseDist));
                adversaryLabels.putRow(i, Nd4j.create(new double[]{1,0}));
            } else {
                generatorFeatures.putRow(i, Nd4j.rand(new int[]{numInputs}, actualDist));
                adversaryLabels.putRow(i, Nd4j.create(new double[]{0,1}));
            }
        }
        MultiDataSet dataSet = new MultiDataSet(new INDArray[]{generatorFeatures},new INDArray[]{adversaryLabels,generatorFeatures});

        for(int i = 0; i < 1000; i++) {
            System.out.println("Starting iteration: "+i);
            graph.fit(dataSet);
            //double score = Stream.of(graph.output(false,dataSet.getFeatures())
            System.out.println("Score: "+graph.score());
        }
    }
}
