package tools;

import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import seeding.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

/**
 * Created by ehallmark on 7/20/16.
 */
public class VectorHelper {
    private static TokenizerFactory tokenizerFactory;
    private static VocabCache<VocabWord> vocab;
    static {
        tokenizerFactory=new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    public static Double[] computeAvgWordVectorsFrom(ParagraphVectors vectors, String label) {
        Double[] data = null;
        if(label!=null) {
            data = toObject(vectors.getLookupTable().vector(label).data().asDouble());
        }
        return data;
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

    public static int[] toPrim(Integer[] objArray) {
        if(objArray==null) return null;
        int[] vec = new int[objArray.length];
        int i = 0;
        for(int d: objArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }

    public static double[][] toPrim(Double[][] array) {
        if(array==null) return null;
        double[][] newArray = new double[array.length][];
        for(int i = 0; i < array.length; i++) {
            newArray[i] = toPrim(array[i]);
        }
        return newArray;
    }


    public static PatentVectors getPatentVectors(ResultSet resultSet, ParagraphVectors wordVectors) throws SQLException, InterruptedException, ExecutionException {
        // Pub Doc Number
        String pubDocNumber = resultSet.getString(1);

        // Publication Date
        Integer pubDate = resultSet.getInt(2);

        PatentVectors p = new PatentVectors(pubDocNumber,pubDate);

        // Invention Title
        String titleText = resultSet.getString(3);
        VectorBuilderThread titleThread = null;
        if(titleText!=null) titleThread = new VectorBuilderThread(wordVectors, titleText);
        if(titleThread!=null) {
            titleThread.fork();
        }

        // Abstract
        String abstractText = resultSet.getString(4);
        VectorBuilderThread abstractThread = null;
        if(!shouldRemoveSentence(abstractText)) abstractThread = new VectorBuilderThread(wordVectors, abstractText);
        if(abstractThread!=null) {
            abstractThread.fork();
        }

        // Description
        String descriptionText = resultSet.getString(5);
        VectorBuilderThread descriptionThread = null;
        if(!shouldRemoveSentence(descriptionText)) descriptionThread = new VectorBuilderThread(wordVectors, descriptionText);
        if(descriptionThread!=null) {
            descriptionThread.fork();
        }

        if(titleThread!=null)p.setTitleWordVectors(titleThread.get());
        if(abstractThread!=null)p.setAbstractWordVectors(abstractThread.get());
        if(descriptionThread!=null)p.setDescriptionWordVectors(descriptionThread.get());

        return p;
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

}
