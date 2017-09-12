package models.keyphrase_prediction.scorers;

import models.keyphrase_prediction.MultiStem;

import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 9/11/17.
 */
public interface KeywordScorer {
    Map<MultiStem,Double> scoreKeywords(Collection<MultiStem> keywords, float[][] matrix);
}
