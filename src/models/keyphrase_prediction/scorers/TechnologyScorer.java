package models.keyphrase_prediction.scorers;

import models.keyphrase_prediction.MultiStem;
import org.apache.commons.math3.linear.RealMatrix;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/11/17.
 */
public class TechnologyScorer implements KeywordScorer {

    // T is a matrix where rows denote keywords and columns denote cpcs
    @Override
    public Map<MultiStem, Double> scoreKeywords(Collection<MultiStem> keywords, RealMatrix _matrix) {
        INDArray matrix = Nd4j.create(_matrix.getData());
        // get row sums
        INDArray squaredRowSums = Transforms.pow(matrix.sum(1),2,true);
        INDArray sumOfSquares = Transforms.pow(matrix,2,true).sum(1);
        double[] scores = sumOfSquares.divi(squaredRowSums).data().asDouble();
        return keywords.parallelStream().collect(Collectors.toMap(keyword->keyword,keyword->scores[keyword.getIndex()]));
    }
}
