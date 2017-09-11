package models.keyphrase_prediction.scorers;

import models.keyphrase_prediction.MultiStem;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/11/17.
 */
public class TermhoodScorer implements KeywordScorer {
    // expects a 1 dimensional vector of counts
    @Override
    public Map<MultiStem, Double> scoreKeywords(Collection<MultiStem> keywords, INDArray matrix) {
        // get row sums
        double[] wordCountSums = matrix.sum(1).data().asDouble();
        return keywords.parallelStream().collect(Collectors.toMap(keyword->keyword,keyword->{
            double[] Mi = matrix.getRow(keyword.getIndex()).data().asDouble();
            double wordCountSumI = wordCountSums[keyword.getIndex()];
            double score = 0d;
            for(int j = 0; j < Mi.length; j++) {
                if(j==keyword.getIndex()) continue;
                double sumProduct = wordCountSums[j] * wordCountSumI;
                score += sumProduct == 0 ? 0 : (Math.pow(Mi[j] - sumProduct, 2) / sumProduct);
            }
            return score;
        }));
    }
}
