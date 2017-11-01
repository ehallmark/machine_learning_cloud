package models;

import elasticsearch.CreatePatentDBIndex;
import elasticsearch.IngestMongoIntoElasticSearch;
import models.classification_models.UpdateClassificationModels;
import models.similarity_models.UpdateSimilarityModels;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import models.value_models.UpdateValueModels;
import models.model_testing.TestSpecificModels;
import models.value_models.graphical.UpdateGraphicalModels;
import seeding.Database;
import seeding.ai_db_updater.UpdateCompDBAndGatherData;

import java.util.Collection;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateModels {
    public static void main(String[] args) throws Exception {
        runModels(null);
    }

    public static void runModels(Collection<String> onlyUpdateAssets) throws Exception{
        // PRE DATA
        Database.main(null);
        UpdateCompDBAndGatherData.main(null);
        Database.main(null);

        // MODELS
        //UpdateSimilarityModels.updateLatest();
        UpdateClassificationModels.updateLatest(onlyUpdateAssets!=null);
        UpdateValueModels.updateLatest(onlyUpdateAssets);
    }
}
