package models.similarity_models;

import assignee_normalization.human_name_prediction.HumanNamePredictionPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateSimilarityModels {
    public static void main(String[] args) throws Exception {
        Map<String,INDArray> lookupTable = CPCSimilarityVectorizer.getLookupTable();
        List<String> unknownAssets = Database.getAllPatentsAndApplications()
                .parallelStream()
                .filter(asset->!lookupTable.containsKey(asset))
                .collect(Collectors.toList());
        updateLatest(unknownAssets);
    }

    private static void updateLatest(Collection<String> assets) throws Exception {
        // Update CPC encodings
        System.out.println("Updating cpc similarity vectorizer...");
        CPCSimilarityVectorizer.updateLatest(assets);

    }
}
