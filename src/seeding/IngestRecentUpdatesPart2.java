package seeding;

import models.value_models.UpdateValueModels;
import seeding.ai_db_updater.UpdateAll;
import seeding.ai_db_updater.UpdateCompDBAndGatherData;
import seeding.ai_db_updater.UpdateExtraneousComputableAttributeData;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;

/**
 * Created by Evan on 9/29/2017.
 */
public class IngestRecentUpdatesPart2 {
    // Completes the initial seed into mongo
    public static void main(String[] args) {
        File newAssetsFile = IngestRecentUpdatesPart1.newAssetsFile;
        if(!newAssetsFile.exists()) throw new RuntimeException("New assets file does not exist...");

        // update compdb and gather data
        try {
            UpdateCompDBAndGatherData.main(args);
        } catch(Exception e) {
            System.out.println("Error during compdb and gather update...");
            e.printStackTrace();
            System.exit(1);
        }

        // then update everything for the new assets
        Collection<String> newAssets = (Collection<String>)Database.tryLoadObject(newAssetsFile);

        // run value models
        try {
            UpdateValueModels.runModels(newAssets);
        } catch(Exception e) {
            System.out.println("Error during value models...");
            e.printStackTrace();
            System.exit(1);
        }

        // run tech tag (incrementally on new assets)


        // run similarity (inference on new assets)


        // update mongo
        try {
            UpdateExtraneousComputableAttributeData.main(args);
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error updating mongo computable attrs...");
            System.exit(1);
        }

        if(newAssetsFile.exists()) newAssetsFile.delete();
        System.out.println("Updates completed successfully.");
    }
}
