package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/20/16.
 */
public class AvgWordVectorIterator implements Iterator<PatentVectors> {
    private final int startDate;
    private ResultSet resultSet;
    private TokenizerFactory tokenizerFactory;
    private WordVectors wordVectors;
    // used to tag each sequence with own Id

    public AvgWordVectorIterator(WordVectors wordVectors, int startDate) throws SQLException {
        this.wordVectors=wordVectors;
        this.startDate=startDate;
        tokenizerFactory=new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
        reset();
    }

    protected void resetQuery() throws SQLException {
        resultSet = Database.getPatentDataWithTitleAndDate(startDate);
    }

    protected PatentVectors getPatentVectors() throws SQLException {
        // Pub Doc Number
        String pubDocNumber = resultSet.getString(1);

        // Publication Date
        Integer pubDate = resultSet.getInt(2);

        PatentVectors p = new PatentVectors(pubDocNumber,pubDate);

        // Invention Title
        String titleText = resultSet.getString(3);
        if(!shouldRemoveSentence(titleText)) p.setTitleWordVectors(computeAvgWordVectorsFrom(titleText));

        // Abstract
        String abstractText = resultSet.getString(4);
        if(!shouldRemoveSentence(abstractText)) p.setAbstractWordVectors(computeAvgWordVectorsFrom(abstractText));

        // Description
        String descriptionText = resultSet.getString(5);
        if(!shouldRemoveSentence(descriptionText)) p.setDescriptionWordVectors(computeAvgWordVectorsFrom(descriptionText));

        return p;
    }

    private Double[][] computeAvgWordVectorsFrom(String sentence) {
        Double[][] data = new Double[Constants.NUM_ROWS_OF_WORD_VECTORS][];

        if(sentence!=null) {
            final int padding = 2;
            List<String> tokens = tokenizerFactory.create(sentence).getTokens();

            // prefilter
            tokens.removeIf(token->(token==null || !wordVectors.hasWord(token)));

            final int bucketSize = Math.max(1,tokens.size()/Constants.NUM_ROWS_OF_WORD_VECTORS);
            for(int i = 0; i < Constants.NUM_ROWS_OF_WORD_VECTORS; i++) {
                int begin = Math.max(0, i*bucketSize-padding);
                int end = Math.min(tokens.size(), i*bucketSize+bucketSize+padding);
                INDArray wordVector = centroidVector(tokens.subList(begin, end));
                data[i]= toObject(wordVector.data().asDouble());
            }
        }
        return data;

    }

    // MAKE SURE ALL TOKENS EXIST IN THE VOCABULARY!!!
    private INDArray centroidVector(List<String> tokens) {
        INDArray allWords = Nd4j.create(tokens.size(), Constants.VECTOR_LENGTH);
        AtomicInteger cnt = new AtomicInteger(0);
        for (String token : tokens) {
            allWords.putRow(cnt.getAndIncrement(), wordVectors.getWordVectorMatrix(token));
        }
        INDArray mean = allWords.mean(0);
        return mean;
    }

    private Double[] toObject(double[] primArray) {
        if(primArray==null) return null;
        Double[] vec = new Double[primArray.length];
        int i = 0;
        for(double d: primArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }

    protected boolean shouldRemoveSentence(String str) {
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

    @Override
    public PatentVectors next() {
        try {
            // Check patent iterator
            resultSet.next();
            return getPatentVectors();

        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR");
        }
    }

    @Override
    public boolean hasNext() {
        try {
            return ((resultSet == null || !(resultSet.isAfterLast() || resultSet.isLast())));
        } catch (SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR WHILE ITERATING");
        }
    }

    public void reset() {
        try {
            if(resultSet!=null && !resultSet.isClosed()) resultSet.close();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        try {
            resetQuery();
        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("UNABLE TO RESET QUERY");
        }
    }

    public void finish() {
        Database.close();
    }

}
