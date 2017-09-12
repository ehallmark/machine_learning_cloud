package models.keyphrase_prediction.scorers;

import models.keyphrase_prediction.MultiStem;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class TermhoodScorer implements KeywordScorer {
    // expects a 1 dimensional vector of counts
    @Override
    public Map<MultiStem, Double> scoreKeywords(Collection<MultiStem> keywords, float[][] matrix) {
        // get row sums
        if(matrix.length==0||matrix.length!=matrix[0].length||keywords.size()!=matrix.length) {
            throw new RuntimeException("Matrix has invalid dimensions or is non-square.");
        }
        final double[] wordCountSums = new double[keywords.size()];
        IntStream.range(0,matrix.length).parallel().forEach(i->{
            float[] row = matrix[i];
            wordCountSums[i] = IntStream.range(0,row.length).mapToDouble(j->row[j]).sum();
        });

        System.out.println("Word count sums length: "+wordCountSums.length);
        if(wordCountSums.length!=keywords.size()) {
            throw new RuntimeException("Invalid word count sums size. Should be: "+keywords.size());
        }
        return keywords.parallelStream().collect(Collectors.toMap(keyword->keyword,keyword->{
            float[] Mi = matrix[keyword.getIndex()];
            double wordCountSumI = wordCountSums[keyword.getIndex()];
            double score = 0d;
            for(int j = 0; j < keywords.size(); j++) {
                if(j==keyword.getIndex()) continue;
                double sumProduct = wordCountSums[j] * wordCountSumI;
                score += sumProduct == 0 ? 0 : (Math.pow(Mi[j] - sumProduct, 2) / sumProduct);
            }
            return score;
        }));
    }
}
