package models.keyphrase_prediction.scorers;

import models.keyphrase_prediction.MultiStem;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class TechnologyScorer implements KeywordScorer {

    // T is a matrix where rows denote keywords and columns denote cpcs
    @Override
    public Map<MultiStem, Double> scoreKeywords(Collection<MultiStem> keywords, RealMatrix matrix) {
        // get row sums
        int length = matrix.getRowDimension();
        double[] squaredRowSums = new double[length];
        double[] sumOfSquares = new double[length];
        IntStream.range(0,length).parallel().forEach(i->{
            double[] row = matrix.getRow(i);
            squaredRowSums[i] = DoubleStream.of(row).sum();
            squaredRowSums[i] = Math.pow(squaredRowSums[i],2);
            sumOfSquares[i] = DoubleStream.of(row).map(d->d*d).sum();
        });

        double[] scores = new double[sumOfSquares.length];
        IntStream.range(0,squaredRowSums.length).parallel().forEach(i->{
            double score = sumOfSquares[i]/squaredRowSums[i];
            if(Double.isNaN(score)) score = 0d;
            scores[i]=score;
        });
        return keywords.parallelStream().collect(Collectors.toMap(keyword->keyword,keyword->scores[keyword.getIndex()]));
    }
}
