package models.similarity_models.signatures;

import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 10/28/2017.
 */
public class BuildSimilarityVectors {
    private static final File vectorMapFile = new File(Constants.DATA_FOLDER+"signature_model_vector_map.jobj");
    public static void main(String[] args) throws Exception {
        // test restore model
        System.out.println("Restoring model test");
        SignatureSimilarityModel clone = SignatureSimilarityModel.restoreAndInitModel(SignatureSimilarityModel.MAX_CPC_DEPTH);
        clone.setBatchSize(1000);
        List<String> allAssets = new ArrayList<>(Database.getAllPatentsAndApplications());

        System.out.println("Testing encodings");
        Map<String,INDArray> vectorMap = clone.encode(allAssets);
        System.out.println("Num vectors found: "+vectorMap.size());
        Database.trySaveObject(vectorMap,vectorMapFile);
        vectorMap.entrySet().stream().limit(20).forEach(e->{
            System.out.println(e.getKey()+": "+ Arrays.toString(e.getValue().data().asFloat()));
        });
    }
}
