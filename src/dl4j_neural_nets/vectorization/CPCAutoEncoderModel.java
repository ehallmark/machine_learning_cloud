package dl4j_neural_nets.vectorization;

import com.google.common.util.concurrent.AtomicDouble;
import dl4j_neural_nets.iterators.datasets.CPCVectorDataSetIterator;
import dl4j_neural_nets.listeners.CustomAutoEncoderListener;
import graphical_models.classification.CPCKMeans;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.spark.api.TrainingMaster;
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer;
import org.deeplearning4j.spark.impl.paramavg.ParameterAveragingTrainingMaster;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 6/1/17.
 */
public class CPCAutoEncoderModel {
    public static final File modelFile = new File("data/cpc_auto_encoder_model.jobj");
    public static final File classificationsFile = new File("data/cpc_auto_encoder_classifications_list.jobj");
    private static MultiLayerNetwork MODEL;
    private static List<String> CLASSIFICATIONS;

    public static MultiLayerNetwork getModel() {
        if(MODEL==null) {
            try {
                System.out.println("Loading auto-encoder");
                MODEL = ModelSerializer.restoreMultiLayerNetwork(modelFile, true);
            } catch (Exception e) {
                System.out.println("Unable to load auto encoder");
                e.printStackTrace();
            }
        }
        return MODEL;
    }

    public static List<String> getOrderedClassifications() {
        if(CLASSIFICATIONS==null) {
            CLASSIFICATIONS=(List<String>) Database.tryLoadObject(classificationsFile);
        }
        return CLASSIFICATIONS;
    }

    public static void main(String[] args) {
        // Fetch pre data
        int sampleSize = 100000;

        // Get Patents
        List<String> patents = new ArrayList<>(Database.getPatentToClassificationMap().keySet());
        Collections.shuffle(patents);
        patents=patents.subList(0,Math.min(sampleSize,patents.size()));

        int batchSize = 10;
        final int nEpochs = 100;
        final int cpcDepth = CPCKMeans.DEFAULT_CPC_DEPTH;
        int printIterations = 100;

        // Split data
        List<String> testSet = patents.subList(patents.size()/2,patents.size());
        patents=patents.subList(0,patents.size()/2);

        // Get Classifications
        List<String> classifications = CPCKMeans.getClassifications(patents,cpcDepth,true);
        final int numInputs = classifications.size();
        final int vectorSize = numInputs/4;

        // Get Iterator
        CPCVectorDataSetIterator iterator = new CPCVectorDataSetIterator(patents,classifications,batchSize,cpcDepth);
        iterator.reset();

        // Config
        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(69)
                .miniBatch(true)
                .iterations(1).optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(1e-2)
                .updater(Updater.RMSPROP).rmsDecay(0.95)
                .weightInit(WeightInit.XAVIER)
                .regularization(true).l2(1e-4)
                .list()
                .layer(0, new VariationalAutoencoder.Builder()
                        .activation(Activation.SIGMOID)
                        .encoderLayerSizes(numInputs/2, numInputs/2)        //2 encoder layers
                        .decoderLayerSizes(numInputs/2, numInputs/2)        //2 decoder layers
                        .pzxActivationFunction(Activation.IDENTITY)  //p(z|data) activation function
                        .reconstructionDistribution(new BernoulliReconstructionDistribution(Activation.SIGMOID.getActivationFunction()))     //Bernoulli distribution for p(data|z) (binary or 0 to 1 data only)
                        .nIn(numInputs)                       //Input size: 28x28
                        .nOut(vectorSize)                            //Size of the latent variable space: p(z|x). 2 dimensions here for plotting, use more in general
                        .build())
                .pretrain(true).backprop(false).build();

        // Build and train network
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
       // network.setListeners(new CustomAutoEncoderListener(printIterations));


        INDArray testMatrix = Nd4j.create(testSet.size(),classifications.size());
        for(int i = 0; i <testSet.size(); i++) {
            testMatrix.putRow(i,Nd4j.create(CPCKMeans.classVectorForPatents(Arrays.asList(testSet.get(i)),classifications,cpcDepth)));
        }

        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder autoencoder
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) network.getLayer(0);

        System.out.println("Train model....");
        double bestErrorSoFar = 2.0d;
        for( int i=0; i<nEpochs; i++ ) {
            network.fit(iterator);
            iterator.reset();
            AtomicInteger numErrors = new AtomicInteger(0);
            System.out.println("*** Completed epoch {"+i+"} ***");
            double overallError = testSet.stream().collect(Collectors.averagingDouble(test -> {
                INDArray latentValues = autoencoder.activate(testMatrix.dup(), false);
                INDArray reconstruction = autoencoder.generateAtMeanGivenZ(latentValues);

                double error = 0d;
                for (int r = 0; r < testMatrix.rows(); r++) {
                    double sim = Transforms.cosineSim(testMatrix.getRow(r), reconstruction.getRow(r));
                    if(Double.isNaN(sim)) {
                        numErrors.getAndIncrement();
                        sim=-1d;
                    }
                    error += 1.0 - sim;
                }
                error /= testMatrix.rows();
                return error;
            }));
            System.out.println("Current model error: "+overallError);
            System.out.println("Num errors: "+numErrors.get());
            if(overallError<bestErrorSoFar)bestErrorSoFar=overallError;
            System.out.println("Best Error So Far: "+bestErrorSoFar);

        }
        System.out.println("****************Model finished********************");

        System.out.println("Saving model...");
        try {
            ModelSerializer.writeModel(network, modelFile, true);
            System.out.println("Saved.");
        } catch(Exception e) {
            System.out.println("Unable to save file.");
            e.printStackTrace();
        }

        System.out.println("Saving cpc list");
        Database.trySaveObject(classifications,classificationsFile);
        System.out.println("Saved.");
    }
}
