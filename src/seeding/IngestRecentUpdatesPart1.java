package seeding;

import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;
import models.similarity_models.UpdateSimilarityModels;
import models.value_models.UpdateValueModels;
import org.bson.Document;
import seeding.ai_db_updater.UpdateAll;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Evan on 9/29/2017.
 */
public class IngestRecentUpdatesPart1 {
    static File newAssetsFile = new File("newest_assets.jobj");
    public static void main(String[] args) {
        String[] updates = new String[]{"-1","1","2","3","4","5","6","7","8","9","10","11"};

        Collection<String> newAssets;
        try {

            Collection<String> oldAssets = new HashSet<>();
            oldAssets.addAll(new AssetToFilingMap().getPatentDataMap().keySet());
            oldAssets.addAll(new AssetToFilingMap().getApplicationDataMap().keySet());
            UpdateAll.main(updates);
            newAssets = new HashSet<>();
            newAssets.addAll(new AssetToFilingMap().getPatentDataMap().keySet());
            newAssets.addAll(new AssetToFilingMap().getApplicationDataMap().keySet());
            newAssets.removeAll(oldAssets);
            System.out.println("Num new assets: " + newAssets.size());
            Database.trySaveObject(newAssets, newAssetsFile);

            try {
                // add encodings
                UpdateSimilarityModels.updateLatest(newAssets);
            } catch(Exception e) {
                System.out.println("Error updating encodings...");
                e.printStackTrace();
            }
        } catch(Exception e) {
            System.out.println("Error during seeding...");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
