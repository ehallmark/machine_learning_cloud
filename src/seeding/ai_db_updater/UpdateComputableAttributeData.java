package seeding.ai_db_updater;

import user_interface.server.SimilarPatentServer;
/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateComputableAttributeData {
    private static final int batchSize = 10000;
    public static void main(String[] args) {
        SimilarPatentServer.initialize(false,false);
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(SimilarPatentServer.getAllComputableAttributes(),batchSize);
    }

}
