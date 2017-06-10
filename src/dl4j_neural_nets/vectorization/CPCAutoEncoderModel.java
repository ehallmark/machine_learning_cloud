package dl4j_neural_nets.vectorization;

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
        List<String> classifications = CPCKMeans.getClassifications(patents,cpcDepth);
        final int numInputs = classifications.size();
        final int vectorSize = numInputs/4;

        // Get Iterator
        CPCVectorDataSetIterator iterator = new CPCVectorDataSetIterator(patents,classifications,batchSize,cpcDepth);
        iterator.reset();

        // Config
        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(69)
                .iterations(1)
                .optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT)
                .list()
                .layer(0, new RBM.Builder().nIn(numInputs).nOut(numInputs/2).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
                .layer(1, new RBM.Builder().nIn(numInputs/2).nOut(vectorSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build()) //encoding stops
                .layer(2, new RBM.Builder().nIn(vectorSize).nOut(numInputs/2).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build()) //decoding starts
                .layer(3, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).activation(Activation.SOFTMAX).nIn(numInputs/2).nOut(numInputs).build())
                .pretrain(false)
                .backprop(true)
                .build();

        // Build and train network
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
       // network.setListeners(new CustomAutoEncoderListener(printIterations));


        INDArray testMatrix = Nd4j.create(testSet.size(),classifications.size());
        for(int i = 0; i <testSet.size(); i++) {
            testMatrix.putRow(i,Nd4j.create(CPCKMeans.classVectorForPatents(Arrays.asList(testSet.get(i)),classifications,cpcDepth)));
        }

        double bestErrorSoFar = 2.0d;
        for( int i=0; i<nEpochs; i++ ) {
            while(iterator.hasNext()) {
                network.fit(iterator.next());
            }
            iterator.reset();
            System.out.println("*** Completed epoch {"+i+"} ***");
            double overallError = testSet.stream().collect(Collectors.averagingDouble(test -> {
                INDArray reconstruction = network.activate(testMatrix,false);

                double error = 0d;
                for (int r = 0; r < testMatrix.rows(); r++) {
                   // System.out.println("Actual: "+testMatrix.getRow(r));
                   // System.out.println("Reconstruction: "+reconstruction.getRow(r));
                    double sim = Transforms.cosineSim(testMatrix.getRow(r), reconstruction.getRow(r));
                    if(Double.isNaN(sim)) sim=-1d;
                    error += 1.0 - sim;
                }
                error /= testMatrix.rows();
                return error;
            }));
            System.out.println("Current model error: "+overallError);
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
