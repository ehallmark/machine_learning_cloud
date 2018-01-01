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
        // run value models
        List<String> updates;
        try {
            updates = UpdateModels.runModels(false);
        } catch(Exception e) {
            System.out.println("Error during value models...");
            e.printStackTrace();
            updates = null;
            System.exit(1);
        }

        // update mongo
        try {
            if(updates!=null) {
                UpdateExtraneousComputableAttributeData.update(updates);
            }
        } catch(Exception e) {
            System.out.println("Error updating mongo computable attrs...");
            e.printStackTrace();
            System.exit(1);
        }

        // update elasticsearch
        if(updates!=null) {
            updateElasticSearch(updates);
        }

        System.out.println("Updates completed successfully.");
    }

    public static void updateElasticSearch(Collection<String> newAssets) {
        try {
            if(newAssets==null) {
                IngestMongoIntoElasticSearch.ingestByType(DataIngester.PARENT_TYPE_NAME);
                IngestMongoIntoElasticSearch.ingestByType(DataIngester.TYPE_NAME);
            } else {
                List<String> filings = newAssets.parallelStream().map(asset -> new AssetToFilingMap().getPatentDataMap().getOrDefault(asset, new AssetToFilingMap().getApplicationDataMap().get(asset))).filter(a -> a != null).collect(Collectors.toList());
                List<String> assets = newAssets.stream().collect(Collectors.toList());
                Document parentQuery = new Document("_id", new Document("$in", filings));
                Document query = new Document("_id", new Document("$in", assets));
                IngestMongoIntoElasticSearch.ingestByType(DataIngester.PARENT_TYPE_NAME, parentQuery);
                IngestMongoIntoElasticSearch.ingestByType(DataIngester.TYPE_NAME, query);
            }
        } catch(Exception e) {
            System.out.println("Error during elasticsearch ingest...");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
