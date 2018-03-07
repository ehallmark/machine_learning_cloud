package models.dl4j_neural_nets.vectorization.auto_encoders;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import models.similarity_models.class_vectors.CPCSimilarityFinder;

import java.io.File;
import java.util.*;

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
