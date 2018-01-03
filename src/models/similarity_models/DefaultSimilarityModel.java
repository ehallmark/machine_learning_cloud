package models.similarity_models;

import models.similarity_models.combined_similarity_model.CombinedSimilarityVAEPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/31/17.
 */
public class DefaultSimilarityModel extends BaseSimilarityModel {

    public DefaultSimilarityModel(Collection<String> candidateSet) {
        super(candidateSet.stream().map(str->new Item(str)).collect(Collectors.toList()),
                new CPCSimilarityVectorizer(CombinedSimilarityVAEPipelineManager.getOrLoadManager(), false, false, false,null));
    }
}
