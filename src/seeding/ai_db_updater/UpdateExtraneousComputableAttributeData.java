package seeding.ai_db_updater;

import models.similarity_models.Vectorizer;
import models.similarity_models.combined_similarity_model.CombinedSimilarityVAEPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.computable_attributes.NestedComputedCPCAttribute;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;
import user_interface.ui_models.attributes.script_attributes.SimilarityAttribute;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateExtraneousComputableAttributeData {
    public static void update(List<String> assets) {
        SimilarPatentServer.initialize(true,false);

        CombinedSimilarityVAEPipelineManager combinedSimilarityVAEPipelineManager = CombinedSimilarityVAEPipelineManager.getOrLoadManager();
        Vectorizer combinedVectorizer = new CPCSimilarityVectorizer(combinedSimilarityVAEPipelineManager,false, true, false,null);

        Map<String,Vectorizer> vectorizerMap = Collections.synchronizedMap(new HashMap<>());
        //vectorizerMap.put("vector_obj",cpcVaeVectorizer);
        vectorizerMap.put(SimilarityAttribute.VECTOR_NAME,combinedVectorizer);

        List<ComputableAttribute<?>> computableAttributes = SimilarPatentServer.getAllComputableAttributes().stream().filter(a->!(a instanceof HiddenAttribute)).collect(Collectors.toCollection(ArrayList::new));
        computableAttributes.add(new NestedComputedCPCAttribute());
        // add cpc nested attr
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(computableAttributes,vectorizerMap,assets==null?null:Collections.synchronizedSet(new HashSet<>(assets)));
    }

}
