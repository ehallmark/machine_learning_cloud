package seeding.ai_db_updater;

import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.*;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;
import user_interface.ui_models.attributes.script_attributes.FastSimilarityAttribute;
import user_interface.ui_models.attributes.script_attributes.SimilarityAttribute;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateExtraneousComputableAttributeData {
    public static void update(List<String> assets) {
        SimilarPatentServer.initialize(true,false);

        DeepCPCVAEPipelineManager similarityVAEPipelineManager = DeepCPCVAEPipelineManager.getOrLoadManager();
        Vectorizer combinedVectorizer = new CPCSimilarityVectorizer(similarityVAEPipelineManager,false, true, false,null);

        Map<String,Vectorizer> vectorizerMap = Collections.synchronizedMap(new HashMap<>());
        //vectorizerMap.put("vector_obj",cpcVaeVectorizer);
        vectorizerMap.put(FastSimilarityAttribute.VECTOR_NAME,combinedVectorizer);
        vectorizerMap.put(SimilarityAttribute.VECTOR_NAME,combinedVectorizer);

        List<ComputableAttribute<?>> computableAttributes = SimilarPatentServer.getAllComputableAttributes().stream().filter(a->!(a instanceof HiddenAttribute)).collect(Collectors.toCollection(ArrayList::new));
        computableAttributes.add(new NestedComputedCPCAttribute());
        computableAttributes.add(new NestedComputedCompDBAttribute());
        computableAttributes.add(new TermAdjustmentAttribute());
        computableAttributes.add(new PriorityDateComputedAttribute());

        // add cpc nested attr
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(computableAttributes,vectorizerMap,assets==null?null:Collections.synchronizedSet(new HashSet<>(assets)));
    }

}
