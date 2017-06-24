package similarity_models;

import org.nd4j.linalg.api.ndarray.INDArray;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.List;

/**
 * Created by ehallmark on 5/9/17.
 */
public interface AbstractSimilarityModel {
    double similarityTo(String label);

    PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, int limit, Collection<? extends AbstractFilter> filters);

    int numItems();

    PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, int limit, Collection<? extends AbstractFilter> filters);

    String getName();

    AbstractSimilarityModel duplicateWithScope(Collection<Item> scope);

    List<Item> getItemList();
}
