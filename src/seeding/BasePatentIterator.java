package seeding;

import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/18/16.
 */
public class BasePatentIterator implements LabelAwareSentenceIterator {

    private final int startDate;
    private ResultSet resultSet;
    private String currentPatent;
    private Iterator<String> currentPatentIterator;
    private SentencePreProcessor preProcessor;

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
                return preProcessor.preProcess(currentPatentIterator.next());
            }
            // Check for more results in result set
            resultSet.next();
            currentPatent = resultSet.getString(1);
            int startIndex = 2;
            int endIndex = 4;
            List<String> preIterator = new LinkedList<>();
            for(int i = startIndex; i < endIndex; i++) {
                List<String> sentences = new ArrayList<>(Arrays.asList((String[])resultSet.getArray(i).getArray()));
                sentences.removeIf(str->str==null||str.split("\\s+").length<Constants.MIN_WORDS_PER_SENTENCE);
                if(sentences.size() > Constants.MAX_SENTENCES_PER_DOCUMENT) {
                    // Take a random subset of sentences if document is "too long"
                    Collections.shuffle(sentences);
                    sentences = sentences.subList(0,Constants.MAX_SENTENCES_PER_DOCUMENT);
                }

                // Strip excess input and add to preIterator list
                preIterator.addAll(sentences.stream().map(str->{
                    String[] strArray=str.split("\\s+");
                    if(strArray.length>Constants.MAX_WORDS_PER_SENTENCE) {
                        return String.join(" ",Arrays.copyOfRange(strArray, 0, Constants.MAX_WORDS_PER_SENTENCE));
                    } else {
                        return str;
                    }
                }).collect(Collectors.toList()));

            }
            currentPatentIterator = preIterator.iterator();
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
