package dl4j_neural_nets.vectorization.auto_encoders;

import dl4j_neural_nets.iterators.datasets.CPCVectorDataSetIterator;
import dl4j_neural_nets.listeners.CustomAutoEncoderListener;
import graphical_models.classification.CPCKMeans;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.AutoEncoder;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import similarity_models.cpc_vectors.CPCSimilarityFinder;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 6/1/17.
 */
public class CPCDeepBeliefAutoEncoderModel {
    public static final File modelFile = new File("data/cpc_auto_encoder_model.jobj");
    private static MultiLayerNetwork MODEL;

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

    public static void main(String[] args) {
        Map<String,INDArray> lookupTable = CPCSimilarityFinder.getRawLookupTable();

        // Fetch pre data
        int sampleSize = 50000;
        int numTests = 5000;

        // Get Patents
        List<String> patents = new ArrayList<>(Database.getPatentToClassificationMap().keySet());
        Collections.shuffle(patents);
        patents=patents.subList(0,Math.min(sampleSize,patents.size()));

        int batchSize = 10;
        final int nEpochs = 100;
        int printIterations = 1000;

        // Split data
        List<String> testSet = patents.subList(0,numTests).stream().filter(patent->lookupTable.containsKey(patent)).collect(Collectors.toList());
        patents=patents.subList(numTests,patents.size()).stream().filter(patent->lookupTable.containsKey(patent)).collect(Collectors.toList());

        // Get Classifications
        final int numInputs = lookupTable.values().stream().findAny().get().length();
        final int vectorSize = numInputs/4;

        System.out.println("Num Inputs: "+numInputs);
        System.out.println("Vector Size: "+vectorSize);
        System.out.println("Num Examples: "+patents.size());
        System.out.println("Num Tests: "+testSet.size());


        // Get Iterator
        DataSetIterator iterator = new CPCVectorDataSetIterator(patents, lookupTable,batchSize,numInputs);

        // Config
        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(69)
                .iterations(1)
                .learningRate(0.001)
                .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                .gradientNormalizationThreshold(1d)
                .miniBatch(true)
                .updater(Updater.NESTEROVS)
                .momentum(0.5)
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .list()
                .layer(0, new RBM.Builder().nIn(numInputs).nOut(vectorSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
                .layer(1, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).activation(Activation.SOFTMAX).nIn(vectorSize).nOut(numInputs).build())
                .pretrain(true).backprop(true)
                .build();

        // Build and train network
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.setListeners(new CustomAutoEncoderListener(printIterations));

        INDArray testMatrix = Nd4j.create(testSet.size(),numInputs);
        for(int i = 0; i <testSet.size(); i++) {
            testMatrix.putRow(i,lookupTable.get(testSet.get(i)));
        }

        System.out.println("Train model....");
        double bestErrorSoFar = 2.0d;
        Double startingError = null;
        List<Double> errorsList = new ArrayList<>(nEpochs);
        for( int i=0; i<nEpochs; i++ ) {
            network.fit(iterator);

            AtomicInteger numErrors = new AtomicInteger(0);
            System.out.println("*** Starting epoch {"+i+"} ***");
            INDArray reconstruction = network.activateSelectedLayers(0,network.getnLayers()-1,testMatrix);

            //System.out.println("Reconstruction: "+reconstruction.getRow(0));
            //System.out.println("Should be: "+testMatrix.getRow(0));
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
            double overallError = error;
            errorsList.add(overallError);
            if(startingError==null) startingError=overallError;
            System.out.println("Num errors: "+numErrors.get());
            if(overallError<bestErrorSoFar){
                bestErrorSoFar=overallError;
                System.out.println("FOUND BETTER MODEL");
                saveModel(network);

            }
            System.out.println("Starting error: "+startingError);
            System.out.println("Avg Error: "+errorsList.stream().collect(Collectors.averagingDouble(d->d)));
            System.out.println("Current model error: "+overallError);
            System.out.println("Best Error So Far: "+bestErrorSoFar);

        }
        System.out.println("****************Model finished********************");

        saveModel(network);

        System.out.println("Saved.");
    }

    public static void saveModel(MultiLayerNetwork network) {
        System.out.println("Saving model...");
        try {
            ModelSerializer.writeModel(network, modelFile, true);
            System.out.println("Saved.");
        } catch(Exception e) {
            System.out.println("Unable to save file.");
            e.printStackTrace();
        }

    }
}
