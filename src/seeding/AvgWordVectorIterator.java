package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import tools.VectorHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by ehallmark on 7/20/16.
 */
public class AvgWordVectorIterator implements Iterator<PatentVectors> {
    private final int startDate;
    private ResultSet resultSet;
    private static TokenizerFactory tokenizerFactory;
    static {
        tokenizerFactory=new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }
    private WordVectors wordVectors;

    public AvgWordVectorIterator(WordVectors wordVectors, int startDate) throws SQLException {
        this.wordVectors=wordVectors;
        this.startDate=startDate;
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
        if(!shouldRemoveSentence(titleText)) p.setTitleWordVectors(VectorHelper.computeAvgWordVectorsFrom(wordVectors,titleText));

        // Abstract
        String abstractText = resultSet.getString(4);
        if(!shouldRemoveSentence(abstractText)) p.setAbstractWordVectors(VectorHelper.compute2DAvgWordVectorsFrom(wordVectors,abstractText));

        // Description
        String descriptionText = resultSet.getString(5);
        if(!shouldRemoveSentence(descriptionText)) p.setDescriptionWordVectors(VectorHelper.compute2DAvgWordVectorsFrom(wordVectors,descriptionText));

        return p;
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
            if(wordCount >= 1) return false;
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
