package tools;

import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import seeding.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Created by ehallmark on 7/20/16.
 */
public class VectorHelper {
    private static TokenizerFactory tokenizerFactory;
    static {
        tokenizerFactory=new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    public static Float[] computeAvgWordVectorsFrom(ParagraphVectors vectors, String label) {
        if(label!=null) {
            return toObject(vectors.getLookupTable().vector(label).data().asFloat());
        } else return null;
    }

    private static Float[] toObject(float[] primArray) {
        if(primArray==null) return null;
        Float[] vec = new Float[primArray.length];
        int i = 0;
        for(float d: primArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }

    public static float[] toPrim(Float[] objArray) {
        if(objArray==null) return null;
        float[] vec = new float[objArray.length];
        int i = 0;
        for(float d: objArray) {
            vec[i] = d;
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
