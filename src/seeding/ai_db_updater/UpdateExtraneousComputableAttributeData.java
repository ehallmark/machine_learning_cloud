package seeding.ai_db_updater;

import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;

import java.util.stream.Collectors;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateExtraneousComputableAttributeData {
    public static void main(String[] args) {
        SimilarPatentServer.initialize(true,false);
        Vectorizer vectorizer = new CPCSimilarityVectorizer(false, true, false);
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(SimilarPatentServer.getAllComputableAttributes().stream().filter(a->!(a instanceof HiddenAttribute)).collect(Collectors.toList()),vectorizer);
    }

}
