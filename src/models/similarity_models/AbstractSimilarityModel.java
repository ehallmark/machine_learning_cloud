package models.similarity_models;

import org.nd4j.linalg.api.ndarray.INDArray;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;

/**
 * Created by ehallmark on 5/9/17.
 */
public interface AbstractSimilarityModel {
    double similarityTo(String label);

    PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, int limit, Collection<? extends AbstractFilter> filters);

    int numItems();

    PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, int limit, Collection<? extends AbstractFilter> filters);

    String getName();

    AbstractSimilarityModel duplicateWithScope(Item[] scope);

    Item[] getItemList();
}
