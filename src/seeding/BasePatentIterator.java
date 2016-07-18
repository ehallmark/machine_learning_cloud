package seeding;

import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by ehallmark on 7/18/16.
 */
public class BasePatentIterator implements LabelAwareSentenceIterator {

    private final int startDate;
    private ResultSet resultSet;
    private String currentPatent;
    private SentencePreProcessor preProcessor;
    private Iterator<String> currentPatentIterator;

    public BasePatentIterator(int startDate) throws SQLException {
        this.startDate=startDate;
        this.preProcessor = new MyPreprocessor();
        Database.setupSeedConn();
    }

    public void resetQuery() throws SQLException {
        resultSet = Database.getPatentVectorData(startDate);
    }

    @Override
    public String currentLabel() {
        return currentPatent;
    }

    @Override
    public List<String> currentLabels() {
        return null;
    }

    @Override
    public String nextSentence() {
        try {
            // Check patent iterator
            if(currentPatentIterator!=null && currentPatentIterator.hasNext()) {
                if(preProcessor!=null)return preProcessor.preProcess(currentPatentIterator.next());
                else return currentPatentIterator.next();
            }
            // Check for more results in result set
            resultSet.next();
            currentPatent = resultSet.getString(1);
            int startIndex = 2;
            int endIndex = 4;
            List<String> preIterator = new LinkedList<>();
            for(int i = startIndex; i < endIndex; i++) {
                List<String> sentences = new ArrayList<>(Arrays.asList(resultSet.getString(i).replaceAll("fig\\.","fig").split("\\.")));

                // Strip end of truncated description
                if(sentences.size() > 1)sentences.remove(sentences.size()-1);

                // Remove bad sentences
                sentences.removeIf(str->sentenceLengthCheck(str));

                // Add to preIterator list
                preIterator.addAll(sentences);

            }
            currentPatentIterator = preIterator.iterator();
            return nextSentence();

        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR");
        }
    }

    public boolean sentenceLengthCheck(String str) {
        if(str==null)return false;
        boolean wasChar = false;
        int wordCount = 0;
        for(Character c : str.toCharArray()) {
            if(Character.isSpaceChar(c) && wasChar) {
                wordCount++;
                wasChar = false;
            } else if(Character.isAlphabetic(c)) {
                wasChar = true;
            }
            if(wordCount >= Constants.MIN_WORDS_PER_SENTENCE) return true;
        }
        return false;
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
