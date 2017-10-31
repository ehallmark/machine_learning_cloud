package models.similarity_models;

import models.similarity_models.signatures.CPCSimilarityVectorizer;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/31/17.
 */
public class DefaultSimilarityModel extends BaseSimilarityModel {

    public DefaultSimilarityModel(Collection<String> candidateSet) {
        super(candidateSet.stream().map(str->new Item(str)).collect(Collectors.toList()),
                new CPCSimilarityVectorizer(false));
    }
}
