package models.keyphrase_prediction.scorers;

import models.keyphrase_prediction.MultiStem;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 9/11/17.
 */
public interface KeywordScorer {
    Map<MultiStem,Double> scoreKeywords(Collection<MultiStem> keywords, INDArray matrix);
}
