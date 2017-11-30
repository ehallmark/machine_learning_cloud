package seeding.ai_db_updater;

import assignee_normalization.name_correction.NormalizeAssignees;
import elasticsearch.DataIngester;
import seeding.CleanseAttributesAndMongoBeforeReseed;
import seeding.Database;
import seeding.compdb.CreateCompDBAssigneeTransactionData;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;

/**
 * Created by Evan on 7/6/2017.
 */
public class UpdateAll {
    public static void main(String[] args) throws Exception {
        try {
            for (String arg : args) {
                if(arg.equals("-2")) {
                    CleanseAttributesAndMongoBeforeReseed.main(args);
                } else if(arg.equals("-1")) {
                    // pg_restore
                    RestoreGatherAndCompDB.main(args);
                } else if(arg.equals("0")) {
                    UpdatePre2005DataFromPatentDB.main(args);
                    DataIngester.finishCurrentMongoBatch();
                } else if (arg.equals("1")) {
                    UpdateBaseApplicationData.main(args);
                    DataIngester.finishCurrentMongoBatch();
                } else if (arg.equals("2")) {
                    UpdateBasePatentData.main(args);
                    DataIngester.finishCurrentMongoBatch();
                } else if (arg.equals("3")) {
                    // update compdb
                    Database.main(args);
                    CreateCompDBAssigneeTransactionData.main(args);
                } else if (arg.equals("4")) {
                    UpdatePairBulkData.main(args);
                } else if (arg.equals("5")) {
                    UpdateMaintenanceFeeData.main(args);
                } else if (arg.equals("6")) {
                    UpdateClassificationHash.main(args);
                } else if (arg.equals("7")) {
                    UpdateWIPOTechnologies.main(args);
                } else if (arg.equals("8")) {
                    UpdateAssignmentData.main(args);
                    DataIngester.finishCurrentMongoBatch();
                    Database.main(args);
                } else if (arg.equals("9")) {
                    NormalizeAssignees.main(args);
                    new AssetToAssigneeMap().save();
                    Database.main(args);
                } else if (arg.equals("10")) {
                    UpdateAssetGraphs.main(args);
                } else if (arg.equals("11")) {
                    UpdateExtraneousComputableAttributeData.main(args);
                }
            }
        } finally {
            DataIngester.close();
        }
    }
}
