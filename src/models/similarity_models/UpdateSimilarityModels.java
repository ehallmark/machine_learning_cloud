package models.similarity_models;

import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;

import java.util.Collection;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateSimilarityModels {
    public static void main(String[] args) throws Exception {
        updateLatest(null);
    }

    public static void updateLatest(Collection<String> assets) throws Exception {
        // Update CPC encodings
        CPCSimilarityVectorizer.updateLatest(assets);
    }
}
