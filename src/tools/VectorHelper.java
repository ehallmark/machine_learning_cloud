package tools;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.IntervalIndex;
import org.nd4j.linalg.indexing.PointIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.MyPreprocessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/20/16.
 */
public class VectorHelper {
    private static TokenizerFactory tokenizerFactory;
    static {
        tokenizerFactory=new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    public static Double[][] compute2DAvgWordVectorsFrom(WordVectors wordVectors, String sentence) {
        Double[][] data = null;

        if(sentence!=null) {
            List<String> tokens = createAndPrefilterTokens(wordVectors,sentence);
            if(tokens.isEmpty()) return null;

            data = new Double[Constants.NUM_ROWS_OF_WORD_VECTORS][];

            final int bucketSize = Math.max(1, tokens.size() / Constants.NUM_ROWS_OF_WORD_VECTORS);
            for (int i = 0; i < Constants.NUM_ROWS_OF_WORD_VECTORS; i++) {
                int begin = i * bucketSize;
                int end = Math.min(tokens.size(), i * bucketSize + bucketSize);
                if(begin>=end) begin=end-1;
                List<String> subList = tokens.subList(begin, end);
                INDArray wordVector = centroidVector(wordVectors, subList);
                data[i] = toObject(wordVector.data().asDouble());
            }

        }
        return data;

    }

    public static Double[][] createAndMerge2DWordVectors(WordVectors wordVectors, String[] sentences) {
        if(sentences==null)return null;
        Double[][] data;

        List<INDArray> validSentences = new ArrayList<>(sentences.length);

        for(String sentence : sentences) {
            List<String> tokens = createAndPrefilterTokens(wordVectors, sentence);
            if(!tokens.isEmpty()) validSentences.add(centroidVector(wordVectors, tokens));
        }

        INDArray allSentences = Nd4j.create(validSentences.size(), Constants.NUM_ROWS_OF_WORD_VECTORS, Constants.VECTOR_LENGTH);
        AtomicInteger cnt = new AtomicInteger(0);

        if(validSentences.isEmpty()) return null;

        validSentences.forEach(sentence->{
            int index = cnt.getAndIncrement();
            for(int row = 0; row < sentence.rows(); row++) {
                for(int col=0; col < sentence.columns(); col++) {
                    allSentences.put(new int[]{index,row,col},sentence.getScalar(row,col));
                }
            }
        });

        data = new Double[Constants.NUM_ROWS_OF_WORD_VECTORS][];

        INDArray mean = allSentences.mean(0);
        if(!mean.isMatrix()) throw new RuntimeException("DIMENSION HAS TO BE 2!!!!");

        for(int row = 0; row < Constants.NUM_ROWS_OF_WORD_VECTORS; row++) {
            Double[] innerRow = toObject(mean.getRow(0).data().asDouble());
            data[row] = innerRow;
        }
        return data;
    }

    public static Double[] computeAvgWordVectorsFrom(WordVectors wordVectors, String sentence) {
        Double[] data = null;
        if(sentence!=null) {
            List<String> tokens = createAndPrefilterTokens(wordVectors,sentence);
            if(!tokens.isEmpty())data = toObject(centroidVector(wordVectors, tokens).data().asDouble());
        }
        return data;
    }

    private static List<String> createAndPrefilterTokens(WordVectors wordVectors, String sentence) {
        List<String> tokens = tokenizerFactory.create(sentence).getTokens();
        // filter
        tokens.removeIf(token->(token==null || !wordVectors.hasWord(token)));
        return tokens;
    }

    // MAKE SURE ALL TOKENS EXIST IN THE VOCABULARY!!!
    private static INDArray centroidVector(WordVectors wordVectors, List<String> tokens) {
        INDArray allWords = Nd4j.create(tokens.size(), Constants.VECTOR_LENGTH);
        AtomicInteger cnt = new AtomicInteger(0);
        for (String token : tokens) {
            allWords.putRow(cnt.getAndIncrement(), wordVectors.getWordVectorMatrix(token));
        }
        INDArray mean = allWords.mean(0);
        return mean;
    }

    private static Double[] toObject(double[] primArray) {
        if(primArray==null) return null;
        Double[] vec = new Double[primArray.length];
        int i = 0;
        for(double d: primArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }

}
