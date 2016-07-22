package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Created by ehallmark on 7/20/16.
 */
public class AvgWordVectorIterator implements Iterator<PatentVectors> {
    private final int startDate;
    private ResultSet resultSet;
    private ResultSet compdbResultSet;
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
        compdbResultSet = Database.getCompDBPatentData();
    }

    protected PatentVectors getPatentVectors(ResultSet resultSet) throws SQLException, InterruptedException, ExecutionException {
        // Pub Doc Number
        String pubDocNumber = resultSet.getString(1);

        // Publication Date
        Integer pubDate = resultSet.getInt(2);

        PatentVectors p = new PatentVectors(pubDocNumber,pubDate);

        // Invention Title
        String titleText = resultSet.getString(3);
        VectorBuilderThread titleThread = null;
        if(!shouldRemoveSentence(titleText)) titleThread = new VectorBuilderThread(wordVectors, titleText);
        if(titleThread!=null) {
            titleThread.fork();
        }

        // Abstract
        String abstractText = resultSet.getString(4);
        VectorBuilderThread2D abstractThread = null;
        if(!shouldRemoveSentence(abstractText)) abstractThread = new VectorBuilderThread2D(wordVectors, abstractText);
        if(abstractThread!=null) {
            abstractThread.fork();
        }

        // Description
        String descriptionText = resultSet.getString(5);
        VectorBuilderThread2D descriptionThread = null;
        if(!shouldRemoveSentence(descriptionText)) descriptionThread = new VectorBuilderThread2D(wordVectors, descriptionText);
        if(descriptionThread!=null) {
            descriptionThread.fork();
        }

        if(titleThread!=null)p.setTitleWordVectors(titleThread.get());
        if(abstractThread!=null)p.setAbstractWordVectors(abstractThread.get());
        if(descriptionThread!=null)p.setDescriptionWordVectors(descriptionThread.get());

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
            if(hasNextCompDB()) {
                compdbResultSet.next();
                return getPatentVectors(compdbResultSet);

            } else {
                resultSet.next();
                return getPatentVectors(resultSet);
            }

        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("ERROR ITERATING NEXT PATENT VECTORS OBJECT");
        }
    }

    @Override
    public boolean hasNext() {
        try {
            return hasNextOriginal() || hasNextCompDB();
        } catch (SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR WHILE ITERATING");
        }
    }

    private boolean hasNextOriginal() throws SQLException {
        return ((resultSet == null || !(resultSet.isAfterLast() || resultSet.isLast())));
    }

    private boolean hasNextCompDB() throws SQLException {
        return ((compdbResultSet == null || !(compdbResultSet.isAfterLast() || compdbResultSet.isLast())));
    }

    public void reset() {
        try {
            if(resultSet!=null && !resultSet.isClosed()) resultSet.close();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        try {
            if(compdbResultSet!=null && !compdbResultSet.isClosed()) compdbResultSet.close();
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
