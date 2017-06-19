package similarity_models.class_vectors;

import classification_models.WIPOHelper;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import similarity_models.BaseSimilarityModel;
import similarity_models.class_vectors.vectorizer.ClassVectorizer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/8/2017.
 */
public class WIPOSimilarityFinder extends BaseSimilarityModel {
    private static final File file = new File("data/wipo_nn_similarity_finder_lookup_table.jobj");
    private static final File rawFile = new File("data/wipo_similarity_finder_lookup_table.jobj");
    private static Map<String,INDArray> LOOKUP_TABLE;
    private static Map<String,INDArray> RAW_LOOKUP_TABLE;
    public WIPOSimilarityFinder(Collection<String> candidateSet, String name) {
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
        Map<String,Collection<String>> dataMap = WIPOHelper.getWIPOMap().entrySet().stream().collect(Collectors.toMap(e->e.getKey(),e->Arrays.asList(e.getValue())));

        AbstractClassSimilarityFinder.trainAndSave(dataMap,classDepth,rawFile);

    }
}
