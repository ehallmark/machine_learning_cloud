package models;

import models.model_testing.TestSpecificModels;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateModels {
    public static void main(String[] args) throws Exception {
        models.similarity_models.UpdateSimilarityModels.main(args);
        models.classification_models.UpdateClassificationModels.main(args);
        models.graphical_models.UpdateGraphicalModels.main(args);
        TestSpecificModels.main(args); // test models
    }
}
