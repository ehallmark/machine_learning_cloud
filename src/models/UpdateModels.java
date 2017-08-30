package models;

import models.value_models.regression.UpdateValueModels;
import models.model_testing.TestSpecificModels;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateModels {
    public static void main(String[] args) throws Exception {
        // pre data
        seeding.ai_db_updater.UpdateAll.main(args);
        seeding.Database.main(args);

        //ParagraphVectorModel.main(args);

        // models
        //models.similarity_models.UpdateSimilarityModels.main(args);
        models.graphical_models.UpdateGraphicalModels.main(args); // page rank
        //models.classification_models.UpdateClassificationModels.main(args);
        UpdateValueModels.main(args);

        // test
        TestSpecificModels.main(args);
    }
}
