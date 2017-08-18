package models;

import seeding.ai_db_updater.UpdateExtraneousComputableAttributeData;
import models.dl4j_neural_nets.vectorization.ParagraphVectorModel;
import models.model_testing.TestSpecificModels;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateModels {
    public static void main(String[] args) throws Exception {
        // pre data
        seeding.ai_db_updater.UpdateAll.main(args);
        seeding.Database.main(args);

        ParagraphVectorModel.main(args);

        // models
        models.similarity_models.UpdateSimilarityModels.main(args);
        models.graphical_models.UpdateGraphicalModels.main(args); // sim rank and page rank
        models.classification_models.UpdateClassificationModels.main(args);
        // TODO update value models
        models.value_models.UpdateValueModels.main(args);

        // update elasticsearch attributes
        UpdateExtraneousComputableAttributeData.main(args);

        // test
        TestSpecificModels.main(args); // test models
    }
}
