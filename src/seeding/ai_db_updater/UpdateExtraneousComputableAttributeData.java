package seeding.ai_db_updater;

import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.computable_attributes.NestedComputedCPCAttribute;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateExtraneousComputableAttributeData {
    public static void main(String[] args) {
        SimilarPatentServer.initialize(true,false);
        Vectorizer vectorizer = new CPCSimilarityVectorizer(false, true, false);
        List<ComputableAttribute<?>> computableAttributes = SimilarPatentServer.getAllComputableAttributes().stream().filter(a->!(a instanceof HiddenAttribute)).collect(Collectors.toCollection(ArrayList::new));
        computableAttributes.add(new NestedComputedCPCAttribute());
        // add cpc nested attr
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(computableAttributes,vectorizer);
    }

}
