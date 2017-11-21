package models.keyphrase_prediction.scorers;

import models.keyphrase_prediction.MultiStem;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class TechnologyScorer implements KeywordScorer {

    // T is a matrix where rows denote keywords and columns denote cpcs
    @Override
    public Map<MultiStem, Double> scoreKeywords(Set<MultiStem> keywords, RealMatrix matrix) {
        // get row sums
        int length = matrix.getRowDimension();
        double[] squaredRowSums = new double[length];
        double[] sumOfSquares = new double[length];
        double[] sums = new double[length];
        AtomicInteger idx = new AtomicInteger(0);
        IntStream.range(0,length).parallel().forEach(i->{
            double[] row = matrix.getRow(i);
            sums[i] = DoubleStream.of(row).sum();
            squaredRowSums[i] = Math.pow(sums[i],2);
            sumOfSquares[i] = DoubleStream.of(row).map(d->d*d).sum();
            if(idx.getAndIncrement()%10000==9999)System.out.println("Finished sumOfSquares: "+idx.get());
        });

        idx.set(0);
        double[] scores = new double[sumOfSquares.length];
        IntStream.range(0,squaredRowSums.length).parallel().forEach(i->{
            double score = sumOfSquares[i]/squaredRowSums[i];
            if(Double.isNaN(score)) score = 0d;
            scores[i]=score;
            if(idx.getAndIncrement()%10000==9999) System.out.println("Finished scoring: "+idx.get());
        });
        return keywords.parallelStream().collect(Collectors.toMap(keyword->keyword,keyword->scores[keyword.getIndex()]));
    }
}
