package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.MongoDBClient;
import user_interface.server.SimilarPatentServer;

/**
 * Created by Evan on 7/6/2017.
 */
public class UpdateAll {
    public static void main(String[] args) throws Exception {
        try {
            for (String arg : args) {
                if (arg.equals("0")) {
                    UpdateBasePatentData.main(args);
                    UpdateBaseApplicationData.main(args);
                    saveComputableAttributes();
                } else if (arg.equals("1")) {
                    UpdatePairBulkData.main(args);
                } else if (arg.equals("2")) {
                    UpdateMaintenanceFeeData.main(args);
                } else if (arg.equals("3")) {
                    UpdateClassificationHash.main(args);
                } else if (arg.equals("4")) {
                    UpdatePriorityAndExpirationData.main(args);
                } else if (arg.equals("5")) {
                    UpdateWIPOTechnologies.main(args);
                } else if (arg.equals("6")) {
                    //UpdateRelatedAssetsGraph.main(args);
                    UpdateAssignmentData.main(args);
                } else if (arg.equals("7")) {
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
