package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import models.assignee.normalization.human_name_prediction.HumanNamePredictionPipelineManager;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.factory.Nd4j;
import seeding.CleanseAttributesAndMongoBeforeReseed;
import seeding.Database;
import seeding.compdb.CreateCompDBAssigneeTransactionData;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 7/6/2017.
 */
public class UpdateAll {
    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);

        try {
            for (String arg : args) {
                if(arg.equals("-2")) {
                    CleanseAttributesAndMongoBeforeReseed.main(args);
                } else if(arg.equals("-1")) {
                    // pg_restore
                    RestoreGatherAndCompDB.main(args);
                } else if(arg.equals("0")) {
                    if(AssetToAssigneeMap.getAssigneeToHumanMap()==null) {
                        AssetToAssigneeMap.setAssigneeToHumanMap(HumanNamePredictionPipelineManager.loadPipelineManager().loadPredictions());
                    }
                    UpdatePre2005DataFromPatentDB.main(args);
                    DataIngester.finishCurrentMongoBatch();
                    if(AssetToAssigneeMap.getAssigneeToHumanMap()!=null) {
                        HumanNamePredictionPipelineManager.loadPipelineManager().savePredictions(AssetToAssigneeMap.getAssigneeToHumanMap());
                    }
                } else if (arg.equals("1")) {
                    if(AssetToAssigneeMap.getAssigneeToHumanMap()==null) {
                        AssetToAssigneeMap.setAssigneeToHumanMap(HumanNamePredictionPipelineManager.loadPipelineManager().loadPredictions());
                    }
                    UpdateBaseApplicationData.main(args);
                    DataIngester.finishCurrentMongoBatch();
                    if(AssetToAssigneeMap.getAssigneeToHumanMap()!=null) {
                        HumanNamePredictionPipelineManager.loadPipelineManager().savePredictions(AssetToAssigneeMap.getAssigneeToHumanMap());
                    }
                } else if (arg.equals("2")) {
                    if(AssetToAssigneeMap.getAssigneeToHumanMap()==null) {
                        AssetToAssigneeMap.setAssigneeToHumanMap(HumanNamePredictionPipelineManager.loadPipelineManager().loadPredictions());
                    }
                    UpdateBasePatentData.main(args);
                    DataIngester.finishCurrentMongoBatch();
                    if(AssetToAssigneeMap.getAssigneeToHumanMap()!=null) {
                        HumanNamePredictionPipelineManager.loadPipelineManager().savePredictions(AssetToAssigneeMap.getAssigneeToHumanMap());
                    }
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
                    // Update human map
                    System.out.println("Updating human model map...");
                    HumanNamePredictionPipelineManager pipelineManager = HumanNamePredictionPipelineManager.loadPipelineManager();
                    System.out.println("Predicting results...");
                    List<String> allAssets = new ArrayList<>(Database.getAllPatentsAndApplications());
                    List<String> allAssignees = new ArrayList<>(Database.getAssignees());
                    List<String> allClassCodes = new ArrayList<>(Database.getClassCodes());
                    Map<String,Boolean> predictions = pipelineManager.updatePredictions(allAssets,allAssignees,allClassCodes);
                    pipelineManager.savePredictions(predictions);

                } else if (arg.equals("10")) {
                    UpdateAssetGraphs.update(false);
                } else if (arg.equals("11")) {
                    UpdateExtraneousComputableAttributeData.update(null);
                }
            }
        } finally {
            DataIngester.close();
        }
    }
}
