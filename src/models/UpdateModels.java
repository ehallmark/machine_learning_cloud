package models;

import models.graphical_models.related_docs.RelatedAssetsGraph;
import models.model_testing.TestSpecificModels;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateModels {
    public static void main(String[] args) throws Exception {
        // pre data
        seeding.ai_db_updater.UpdateAll.main(args);
        seeding.Database.main(args);
        RelatedAssetsGraph.main(args); // updates related asset graph

        // models
        models.similarity_models.UpdateSimilarityModels.main(args);
        models.classification_models.UpdateClassificationModels.main(args);
        models.graphical_models.UpdateGraphicalModels.main(args);

        // test
        TestSpecificModels.main(args); // test models
    }
}
