package models.similarity_models;

import models.dl4j_neural_nets.vectorization.ParagraphVectorModel;
import models.similarity_models.class_vectors.CPCSimilarityFinder;
import models.similarity_models.class_vectors.WIPOSimilarityFinder;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import models.similarity_models.signatures.CPCSimilarityVectorizer;
import seeding.Database;

import java.util.Collection;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateSimilarityModels {
    public static void main(String[] args) throws Exception {
        updateLatest();
    }

    public static void updateLatest() throws Exception {
        // Update CPC encodings
        CPCSimilarityVectorizer.main(null);
    }
}
