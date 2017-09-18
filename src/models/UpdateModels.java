package models;

import elasticsearch.CreatePatentDBIndex;
import elasticsearch.IngestMongoIntoElasticSearch;
import models.value_models.UpdateValueModels;
import models.model_testing.TestSpecificModels;
import models.value_models.graphical.UpdateGraphicalModels;
import seeding.ai_db_updater.UpdateCompDBAndGatherData;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateModels {
    public static void main(String[] args) throws Exception {
        // pre data
        seeding.Database.main(args);

        UpdateCompDBAndGatherData.main(args);
        
        //ParagraphVectorModel.main(args);

        // models
        //models.similarity_models.UpdateSimilarityModels.main(args);
        UpdateGraphicalModels.main(args); // page rank
        //models.classification_models.UpdateClassificationModels.main(args);
        UpdateValueModels.main(args);

        UpdateCompDBAndGatherData.main(args);

        // test
        TestSpecificModels.main(args);
    }
}
