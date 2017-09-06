package models;

import models.value_models.UpdateValueModels;
import models.model_testing.TestSpecificModels;
import models.value_models.graphical.UpdateGraphicalModels;

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
        UpdateGraphicalModels.main(args); // page rank
        //models.classification_models.UpdateClassificationModels.main(args);
        UpdateValueModels.main(args);

        // test
        TestSpecificModels.main(args);
    }
}
