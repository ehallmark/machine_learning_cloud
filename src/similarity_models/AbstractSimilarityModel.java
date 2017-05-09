package similarity_models;

import org.nd4j.linalg.api.ndarray.INDArray;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import ui_models.portfolios.PortfolioList;

import java.util.Collection;
import java.util.List;

/**
 * Created by ehallmark on 5/9/17.
 */
public interface AbstractSimilarityModel {

    List<PortfolioList> similarFromCandidateSets(List<SimilarPatentFinder> others, double threshold, int limit, Collection<String> badAssets, PortfolioList.Type portfolioType);

    PortfolioList similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, Collection<String> badLabels, PortfolioList.Type portfolioType);

    // returns null if patentNumber not found
    PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, Collection<String> labelsToExclude, double threshold, int limit, PortfolioList.Type portfolioType);
}
