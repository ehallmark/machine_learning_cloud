package models.keyphrase_prediction.scorers;

import models.keyphrase_prediction.MultiStem;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/11/17.
 */
public class UnithoodScorer implements KeywordScorer {
    // expects a 1 dimensional vector of counts
    @Override
    public Map<MultiStem, Double> scoreKeywords(Collection<MultiStem> keywords, float[][] matrix) {
        float[] transform = matrix[0];
        return keywords.parallelStream().collect(Collectors.toMap(keyword->keyword,keyword->{
            return transform[keyword.getIndex()]*Math.log(1+keyword.getLength());
        }));
    }
}
