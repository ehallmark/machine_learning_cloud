package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.MongoDBClient;
import seeding.compdb.CreateCompDBAssigneeTransactionData;
import user_interface.server.SimilarPatentServer;

/**
 * Created by Evan on 7/6/2017.
 */
public class UpdateAll {
    public static void main(String[] args) throws Exception {
        try {
            for (String arg : args) {
                if(args.equals("0")) {
                    // udpate compdb
                    CreateCompDBAssigneeTransactionData.main(args);
                } else if (arg.equals("1")) {
                    UpdateBasePatentData.main(args);
                    UpdateBaseApplicationData.main(args);
                } else if (arg.equals("2")) {
                    UpdatePairBulkData.main(args);
                } else if (arg.equals("3")) {
                    UpdateMaintenanceFeeData.main(args);
                } else if (arg.equals("4")) {
                    UpdateClassificationHash.main(args);
                } else if (arg.equals("5")) {
                    UpdatePriorityAndExpirationData.main(args);
                } else if (arg.equals("6")) {
                    UpdateWIPOTechnologies.main(args);
                } else if (arg.equals("7")) {
                    //UpdateRelatedAssetsGraph.main(args);
                    UpdateAssignmentData.main(args);
                } else if (arg.equals("8")) {
                    UpdateExtraneousComputableAttributeData.main(args);
                }
            }
        } finally {
            DataIngester.close();
        }
    }

    private static void saveComputableAttributes() {
        // save computable attributes
        SimilarPatentServer.getAllComputableAttributes().forEach(computableAttribute -> {
            System.out.print("Saving: "+computableAttribute.getName()+" ...");
            computableAttribute.save();
            System.out.println("  Done.");
        });
    }
}
