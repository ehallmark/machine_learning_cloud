package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.MongoDBClient;
import user_interface.server.SimilarPatentServer;

/**
 * Created by Evan on 7/6/2017.
 */
public class UpdateAll {
    public static void main(String[] args) throws Exception {
        UpdateBasePatentData.main(args);
        UpdateBaseApplicationData.main(args);
        UpdatePairBulkData.main(args);
        UpdateMaintenanceFeeData.main(args);
        UpdateClassificationHash.main(args);
        UpdatePriorityAndExpirationData.main(args);
        UpdateWIPOTechnologies.main(args);
        //UpdateRelatedAssetsGraph.main(args);
        UpdateAssignmentData.main(args);
        // save computable attributes
        SimilarPatentServer.getAllComputableAttributes().forEach(computableAttribute -> {
            System.out.print("Saving: "+computableAttribute.getName()+" ...");
            computableAttribute.save();
            System.out.println("  Done.");
        });
        UpdateExtraneousComputableAttributeData.main(args);
        // add in computable attributes
        DataIngester.close();

    }
}
