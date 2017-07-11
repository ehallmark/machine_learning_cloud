package seeding.ai_db_updater;

/**
 * Created by Evan on 7/6/2017.
 */
public class UpdateAll {
    public static void main(String[] args) throws Exception {
        UpdatePatentGrantData.main(args);
        //UpdatePatentApplicationData.main(args);
        UpdateAssignmentData.main(args);
        ConstructAssigneeToPatentsMap.main(args);
        UpdateMaintenanceFeeData.main(args);
        UpdateClassificationHash.main(args);
    }
}
