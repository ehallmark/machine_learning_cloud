package seeding.ai_db_updater;

import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.*;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateExtraneousComputableAttributeData {
    private static final int batchSize = 10000;
    public static void main(String[] args) {
        SimilarPatentServer.initialize(true,false);
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(SimilarPatentServer.getAllComputableAttributes().stream().filter(a->!(a instanceof HiddenAttribute)).collect(Collectors.toList()), batchSize);
    }

}
