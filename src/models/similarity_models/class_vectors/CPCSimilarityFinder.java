package models.similarity_models.class_vectors;

import models.graphical_models.classification.CPCKMeans;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import models.similarity_models.BaseSimilarityModel;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by Evan on 6/8/2017.
 */
public class CPCSimilarityFinder extends BaseSimilarityModel {
    private static final File file = new File("data/cpc_nn_similarity_finder_lookup_table.jobj");
    private static final File rawFile = new File("data/cpc_similarity_finder_lookup_table.jobj");
    private static Map<String,INDArray> LOOKUP_TABLE;
    private static Map<String,INDArray> RAW_LOOKUP_TABLE;
    public CPCSimilarityFinder(Collection<Item> candidateSet, String name) {
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

        int classDepth = CPCKMeans.DEFAULT_CPC_DEPTH;
        System.out.println("Starting to add patent data...");
        Map<String,Set<String>> dataMap = new HashMap<>(Database.getPatentToClassificationMap());
        System.out.println("Starting to add app data...");
        dataMap.putAll(Database.getAppToClassificationMap());
        System.out.println("Starting to train and save...");
        AbstractClassSimilarityFinder.trainAndSave(dataMap,classDepth,rawFile);
    }
}
