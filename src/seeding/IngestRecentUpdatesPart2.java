package seeding;

import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;

import models.UpdateModels;
import models.keyphrase_prediction.KeywordModelRunner;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import models.value_models.UpdateValueModels;
import org.bson.Document;

import seeding.ai_db_updater.UpdateCompDBAndGatherData;
import seeding.ai_db_updater.UpdateExtraneousComputableAttributeData;

import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;


import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 9/29/2017.
 */
public class IngestRecentUpdatesPart2 {
    // Completes the initial seed into mongo
    public static void main(String[] args) {
        // PRE DATA
        UpdateCompDBAndGatherData.update();

        // run value models
        try {
             UpdateModels.runModels();
        } catch(Exception e) {
            System.out.println("Error during value models...");
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("Updates completed successfully.");
    }
    
}
