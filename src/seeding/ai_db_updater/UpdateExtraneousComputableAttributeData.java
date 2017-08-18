package seeding.ai_db_updater;

import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.*;

import java.util.Arrays;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateExtraneousComputableAttributeData {
    private static final int batchSize = 10000;
    public static void main(String[] args) {
        SimilarPatentServer.initialize(true,false);
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(SimilarPatentServer.getAllComputableAttributes(),batchSize);
    }

}
