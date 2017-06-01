package dl4j_neural_nets.vectorization;

import dl4j_neural_nets.iterators.datasets.CPCVectorDataSetIterator;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.spark.api.TrainingMaster;
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer;
import org.deeplearning4j.spark.impl.paramavg.ParameterAveragingTrainingMaster;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.DataSet;
import seeding.Database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 6/1/17.
 */
public class CPCAutoEncoderModel {
    public static void main(String[] args) {
        // Fetch pre data
        int sampleSize = 500;

        // Get Patents
        List<String> patents = new ArrayList<>(Database.getPatentToClassificationMap().keySet());
        Collections.shuffle(patents);
        patents=patents.subList(0,Math.min(sampleSize,patents.size()));

        // Get Classifications
        List<String> classifications = patents.stream().flatMap(p-> Database.classificationsFor(p).stream()).distinct().collect(Collectors.toList());

        // Hyper Parameters
        int batchSize = 10;
        final int nEpochs = 100;
        final int vectorSize = 300;
        final int numInputs = classifications.size();

        // Get Iterator
        CPCVectorDataSetIterator iterator = new CPCVectorDataSetIterator(patents,classifications,batchSize);

        // Config
        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(69)
                .iterations(1).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(1e-2)
                .updater(Updater.RMSPROP).rmsDecay(0.95)
                .weightInit(WeightInit.XAVIER)
                .regularization(true).l2(1e-4)
                .gradientNormalizationThreshold(1.0)
                .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                .miniBatch(true)
                .list()
                .layer(0, new VariationalAutoencoder.Builder()
                        .activation(Activation.RELU)
                        .pzxActivationFunction(Activation.IDENTITY)
                        //.dropOut(0.5)
                        .encoderLayerSizes(1000,1000)
                        .decoderLayerSizes(1000,1000)
                        .reconstructionDistribution(new BernoulliReconstructionDistribution(Activation.SIGMOID.getActivationFunction()))     //Bernoulli distribution for p(data|z) (binary or 0 to 1 data only)
                        .nIn(numInputs)                       //Input size: 28x28
                        .nOut(vectorSize)                            //Size of the latent variable space: p(z|x). 2 dimensions here for plotting, use more in general
                        .build())
                .pretrain(true).backprop(false).build();

        // Build and train network
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.setListeners(new ScoreIterationListener(10));

        for(int epoch = 0; epoch < nEpochs; epoch++) {
            System.out.println("Starting epoch: "+(epoch+1));
            network.fit(iterator);
        }
    }
}
