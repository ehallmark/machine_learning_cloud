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
import seeding.Database;
import similarity_models.class_vectors.CPCSimilarityFinder;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 6/1/17.
 */
public class CPCDeepBeliefAutoEncoderModel extends AutoEncoderModel {
    public static final File modelFile = new File("data/cpc_auto_encoder_model.jobj");
    private static MultiLayerNetwork MODEL;

    public MultiLayerNetwork getModel() {
        if(MODEL==null) {
            MODEL = AutoEncoderModel.loadModel(modelFile);
        }
        return MODEL;
    }

    public static void main(String[] args) {
        Map<String,INDArray> lookupTable = CPCSimilarityFinder.getRawLookupTable();

        // Fetch pre data
        int sampleSize = 5000000;
        int numTests = 50000;
        int batchSize = 5;
        final int nEpochs = 100;
        AutoEncoderModel.trainAndSave(lookupTable,Database.getPatentToClassificationMap().keySet(),sampleSize,numTests,batchSize,nEpochs,modelFile);
    }

}
