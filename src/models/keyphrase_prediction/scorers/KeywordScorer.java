package models.keyphrase_prediction.scorers;

import models.keyphrase_prediction.MultiStem;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 9/11/17.
 */
public interface KeywordScorer {
    Map<MultiStem,Double> scoreKeywords(Set<MultiStem> keywords, RealMatrix matrix);
}
