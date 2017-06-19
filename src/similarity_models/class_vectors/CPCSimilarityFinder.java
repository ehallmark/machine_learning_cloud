package similarity_models.class_vectors;

import classification_models.WIPOHelper;
import graphical_models.classification.CPCKMeans;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import similarity_models.BaseSimilarityModel;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/8/2017.
 */
public class CPCSimilarityFinder extends BaseSimilarityModel {
    private static final File file = new File("data/cpc_nn_similarity_finder_lookup_table.jobj");
    private static final File rawFile = new File("data/cpc_similarity_finder_lookup_table.jobj");
    private static Map<String,INDArray> LOOKUP_TABLE;
    private static Map<String,INDArray> RAW_LOOKUP_TABLE;
    public CPCSimilarityFinder(Collection<String> candidateSet, String name) {
        super(candidateSet,name,getLookupTable());
    }

    public static Map<String,INDArray> getRawLookupTable() {
        if(RAW_LOOKUP_TABLE==null) {
            RAW_LOOKUP_TABLE=(Map<String,INDArray>) Database.tryLoadObject(rawFile);
        }
        return RAW_LOOKUP_TABLE;
    }

    public static Map<String,INDArray> getLookupTable() {
        if(LOOKUP_TABLE==null) {
            LOOKUP_TABLE=(Map<String,INDArray>) Database.tryLoadObject(file);
            if(LOOKUP_TABLE==null) LOOKUP_TABLE = getRawLookupTable();
        }
        return LOOKUP_TABLE;
    }
    public static void main(String[] args) throws IOException {
        Database.initializeDatabase();

        int classDepth = -1; // not using
        Map<String,Set<String>> dataMap = Database.getPatentToClassificationMap();

        AbstractClassSimilarityFinder.trainAndSave(dataMap,classDepth,rawFile);
    }
}
