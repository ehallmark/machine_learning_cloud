package similarity_models;

import org.nd4j.linalg.api.ndarray.INDArray;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;

import java.util.Collection;
import java.util.List;

/**
 * Created by ehallmark on 5/9/17.
 */
public interface AbstractSimilarityModel {

    PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, int limit, PortfolioList.Type portfolioType, Collection<? extends AbstractFilter> filters);

    int numItems();

    PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, PortfolioList.Type portfolioType, int limit, Collection<? extends AbstractFilter> filters);

    String getName();
}
