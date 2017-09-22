package seeding;

import seeding.ai_db_updater.*;
import seeding.compdb.CreateCompDBAssigneeTransactionData;

/**
 * Created by ehallmark on 9/22/17.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        // this class updates everything
        for(String arg : args) {
            if (arg.equals("0")) {
                UpdatePre2005DataFromPatentDB.main(args);
            } else if (arg.equals("1")) {
                // udpate compdb
                Database.main(args);
                CreateCompDBAssigneeTransactionData.main(args);
            } else if (arg.equals("2")) {
                UpdateBaseApplicationData.main(args);
            } else if (arg.equals("3")) {
                UpdateBasePatentData.main(args);
            } else if (arg.equals("4")) {
                UpdatePairBulkData.main(args);
            } else if (arg.equals("5")) {
                UpdateMaintenanceFeeData.main(args);
            } else if (arg.equals("6")) {
                UpdateClassificationHash.main(args);
            } else if (arg.equals("7")) {
                UpdateRelatedAssetsGraph.main(args);
            } else if (arg.equals("8")) {
                UpdateWIPOTechnologies.main(args);
            } else if (arg.equals("9")) {
                UpdateAssignmentData.main(args);
            }
        }
    }
}
