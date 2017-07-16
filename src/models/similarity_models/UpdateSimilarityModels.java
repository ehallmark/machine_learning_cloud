package models.similarity_models;

import models.dl4j_neural_nets.vectorization.ParagraphVectorModel;
import models.similarity_models.class_vectors.CPCSimilarityFinder;
import models.similarity_models.class_vectors.WIPOSimilarityFinder;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import seeding.Database;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateSimilarityModels {
    public static void main(String[] args) throws Exception {
        // Latest Database
        Database.main(args);

        // Update lookup tables
        SimilarPatentFinder.main(args);
        //CPCSimilarityFinder.main(args);
        //WIPOSimilarityFinder.main(args);
    }
}
