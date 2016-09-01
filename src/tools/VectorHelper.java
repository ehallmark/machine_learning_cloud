package tools;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;
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

    public static Float[] toObject(float[] primArray) {
        if(primArray==null) return null;
        Float[] vec = new Float[primArray.length];
        int i = 0;
        for(float d: primArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }

    // MAKE SURE ALL TOKENS EXIST IN THE VOCABULARY!!!
    public static INDArray TFIDFcentroidVector(WeightLookupTable<VocabWord> lookupTable, VocabCache<VocabWord> vocab, List<String> tokens) {
        assert vocab.totalNumberOfDocs() > 0 : "There are 0 documents in this vocab!!!";
        INDArray allWords = Nd4j.create(tokens.size(), Constants.VECTOR_LENGTH);
        double total = 0.0;
        AtomicInteger cnt = new AtomicInteger(0);
        for (String token : tokens) {
            int docAppeared = vocab.docAppearedIn(token);
            assert docAppeared > 0 : "Vocab does not have document counts";
            double invDocFreq = Math.log(new Double(vocab.totalNumberOfDocs())/docAppeared);
            total+=invDocFreq;
            allWords.putRow(cnt.getAndIncrement(), lookupTable.vector(token).mul(invDocFreq));
        }
        assert total > 0.0 : "Cannot divide by 0!";
        INDArray mean = allWords.mean(0).div(total/tokens.size());
        return mean;
    }
    public static double[] toPrim(Double[] objArray) {
        if(objArray==null) return null;
        double[] vec = new double[objArray.length];
        int i = 0;
        for(double d: objArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }

    public static float[] toPrim(Integer[] objArray) {
        if(objArray==null) return null;
        float[] vec = new float[objArray.length];
        int i = 0;
        for(int d: objArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }

    public static double[][] toPrim(Double[][] objArray) {
        if(objArray==null) return null;
        double[][] vec = new double[objArray.length][];
        int i = 0;
        for(Double[] d: objArray) {
            vec[i] = toPrim(d);
            i++;
        }
        return vec;
    }

    public static boolean shouldRemoveSentence(String str) {
        if(str==null)return true;
        boolean wasChar = false;
        int wordCount = 0;
        for(Character c : str.toCharArray()) {
            if(Character.isSpaceChar(c) && wasChar) {
                wordCount++;
                wasChar = false;
            } else if(Character.isAlphabetic(c)) {
                wasChar = true;
            }
            if(wordCount >= Constants.MIN_WORDS_PER_SENTENCE) return false;
        }
        return true;
    }

    public static boolean shouldRemoveSentence(List<String> tokens) {
        return(tokens==null || tokens.size() < Constants.MIN_WORDS_PER_SENTENCE);
    }

}
