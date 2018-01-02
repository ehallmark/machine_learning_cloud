package models;

import elasticsearch.CreatePatentDBIndex;
import elasticsearch.IngestMongoIntoElasticSearch;
import models.classification_models.UpdateClassificationModels;
import models.similarity_models.UpdateSimilarityModels;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import models.value_models.UpdateValueModels;
import models.model_testing.TestSpecificModels;
import models.value_models.graphical.UpdateGraphicalModels;
import models.value_models.regression.AIValueModel;
import seeding.Database;
import seeding.ai_db_updater.UpdateCompDBAndGatherData;
import user_interface.ui_models.attributes.computable_attributes.OverallEvaluator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateModels {
    public static void main(String[] args) throws Exception {
        runModels(false);
    }

    public static List<String> runModels(boolean rerunModels) throws Exception{
        // PRE DATA
        //Database.main(null);
        //List<String> gatherCompdbAssets = UpdateCompDBAndGatherData.update();
        //Database.main(null);

        // MODELS
        //UpdateSimilarityModels.updateLatest();
        UpdateClassificationModels.updateLatest();

        Collection<String> unknownAssets = null;
        if(!rerunModels) {
            unknownAssets = Collections.synchronizedCollection(new HashSet<>());
            unknownAssets.addAll(Database.getAllPatentsAndApplications());
            unknownAssets.removeAll(new OverallEvaluator(false).getApplicationDataMap().keySet());
            unknownAssets.removeAll(new OverallEvaluator(false).getPatentDataMap().keySet());
        }
        UpdateValueModels.updateLatest(unknownAssets);

       // if(unknownAssets==null) {
       //     return gatherCompdbAssets;
       // } else if(gatherCompdbAssets==null) {
            return unknownAssets.stream().collect(Collectors.toList());
       // } else {
       //     return Stream.of(unknownAssets,gatherCompdbAssets).flatMap(list->list.stream()).collect(Collectors.toList());
       // }
    }
}
