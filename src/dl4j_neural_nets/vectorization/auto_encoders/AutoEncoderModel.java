package dl4j_neural_nets.vectorization.auto_encoders;

import dl4j_neural_nets.iterators.datasets.ClassificationVectorDataSetIterator;
import dl4j_neural_nets.listeners.CustomAutoEncoderListener;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
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

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 6/1/17.
 */
public abstract class AutoEncoderModel {
    public abstract MultiLayerNetwork getModel();

    public static void trainAndSave(Map<String,INDArray> lookupTable, Collection<String> items, int sampleSize, int numTests, int batchSize, int nEpochs, File file) {


        // Get Items
        List<String> examples = new ArrayList<>(items);
        Collections.shuffle(examples);
        examples=examples.subList(0,Math.min(sampleSize,examples.size()));

        int printIterations = 1000;

        // Split data
        List<String> testSet = examples.subList(0,numTests).stream().filter(patent->lookupTable.containsKey(patent)).collect(Collectors.toList());
        examples=examples.subList(numTests,examples.size()).stream().filter(patent->lookupTable.containsKey(patent)).collect(Collectors.toList());

        // Get Classifications
        final int numInputs = lookupTable.values().stream().findAny().get().length();
        final int vectorSize = numInputs/2;

        System.out.println("Num Inputs: "+numInputs);
        System.out.println("Vector Size: "+vectorSize);
        System.out.println("Num Examples: "+examples.size());
        System.out.println("Num Tests: "+testSet.size());


        // Get Iterator
        DataSetIterator iterator = new ClassificationVectorDataSetIterator(examples, lookupTable, lookupTable, numInputs, numInputs, batchSize);

        // Config
        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(69)
                .iterations(1)
                .learningRate(0.01)
                .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                .gradientNormalizationThreshold(1d)
                .miniBatch(true)
                .updater(Updater.NESTEROVS)
                .momentum(0.7)
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .list()
                .layer(0, new RBM.Builder().nIn(numInputs).updater(Updater.ADAGRAD).dropOut(0.5).nOut(vectorSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
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
            System.out.println("*** STARTING epoch {"+i+"} ***");
            network.fit(iterator);
            System.out.println("*** STARTING TESTS ***");

            AtomicInteger numErrors = new AtomicInteger(0);
            INDArray reconstruction = network.activateSelectedLayers(0,network.getnLayers()-1,testMatrix);
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
                saveModel(network,file);
                System.out.println("Saved.");

            }
            System.out.println("Starting error: "+startingError);
            System.out.println("Avg Error: "+errorsList.stream().collect(Collectors.averagingDouble(d->d)));
            System.out.println("Current model error: "+overallError);
            System.out.println("Best Error So Far: "+bestErrorSoFar);
            System.out.println("*** FINISHED epoch {"+i+"} ***");

        }
        System.out.println("****************Model finished********************");
    }

    public static void saveModel(MultiLayerNetwork network, File modelFile) {
        System.out.println("Saving model...");
        try {
            ModelSerializer.writeModel(network, modelFile, true);
            System.out.println("Saved.");
        } catch(Exception e) {
            System.out.println("Unable to save file.");
            e.printStackTrace();
        }

    }

    public static MultiLayerNetwork loadModel(File modelFile) {
        try {
            System.out.println("Loading auto-encoder");
            return ModelSerializer.restoreMultiLayerNetwork(modelFile, true);
        } catch (Exception e) {
            System.out.println("Unable to load auto encoder");
            e.printStackTrace();
            return null;
        }
    }
}
