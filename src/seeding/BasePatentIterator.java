package seeding;

import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by ehallmark on 7/18/16.
 */
public class BasePatentIterator implements SentenceIterator {

    private final int startDate;
    private ResultSet resultSet;
    private Iterator<String> currentPatentIterator;
    private SentencePreProcessor preProcessor;
    private PreparedStatement claimStatement;
    // used to tag each sequence with own Id

    public BasePatentIterator(int startDate) throws SQLException {
        this.startDate=startDate;
        preProcessor=new MyPreprocessor();
        claimStatement = Database.getClaimsFromPatentStatement();
    }

    protected void resetQuery() throws SQLException {
        resultSet = Database.getPatentVectorData(startDate);
    }


    protected Iterator<String> processedSentenceIterator() throws SQLException {
        List<String> preIterator = new LinkedList<>();
        // Title
        String titleText = resultSet.getString(2);
        if(!shouldRemoveSentence(titleText)) preIterator.add(titleText);

        // Abstract
        String abstractText = resultSet.getString(3);
        if(!shouldRemoveSentence(abstractText)) preIterator.add(abstractText);

        // Description
        String descriptionText = resultSet.getString(4);
        if(!shouldRemoveSentence(descriptionText)) preIterator.add(descriptionText);

        claimStatement.setString(1, resultSet.getString(1));
        ResultSet claimResults = claimStatement.executeQuery();
        while(claimResults.next()) {
            String claimText = claimResults.getString(1);
            if(!shouldRemoveSentence(claimText)) preIterator.add(claimText);
        }
        claimResults.close();

        return preIterator.iterator();
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
    public String nextSentence() {
        try {
            // Check patent iterator
            if(currentPatentIterator!=null && currentPatentIterator.hasNext()) {
                return preProcessor.preProcess(currentPatentIterator.next());
            }
            // Check for more results in result set
            resultSet.next();
            currentPatentIterator = processedSentenceIterator();
            //  System.out.println("Number of sentences for "+currentPatent+": "+preIterator.size());
            return nextSentence();

        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR");
        }
    }

    @Override
    public boolean hasNext() {
        try {
            return ((currentPatentIterator == null || currentPatentIterator.hasNext()) || (resultSet == null || !(resultSet.isAfterLast() || resultSet.isLast())));
        } catch (SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR WHILE ITERATING");
        }
    }

    @Override
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
        currentPatentIterator=null;
    }


    @Override
    public void finish() {
        Database.close();
    }

    @Override
    public SentencePreProcessor getPreProcessor() {
        return preProcessor;
    }

    @Override
    public void setPreProcessor(SentencePreProcessor preProcessor) {
        this.preProcessor=preProcessor;
    }


}
