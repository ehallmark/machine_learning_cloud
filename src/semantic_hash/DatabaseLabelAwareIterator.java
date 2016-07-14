package semantic_hash;

import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;

/**
 * Created by ehallmark on 6/21/16.
 */
public class DatabaseLabelAwareIterator implements LabelAwareSentenceIterator {

    private String patentDBUrl = "jdbc:postgresql://192.168.1.148/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private Connection mainConn;
    private ResultSet results;
    private int limit;
    private int offset;
    private String currentLabel;
    private SentencePreProcessor processor;
    private Iterator<String> innerIterator;
    private final String mainQuery = "SELECT pub_doc_number, abstract FROM patent_grant WHERE abstract is not null AND pub_date BETWEEN to_char(now()::date, 'YYYYMMDD')::int-150000 AND to_char(now()::date, 'YYYYMMDD')::int order by pub_doc_number desc limit ? offset ?";
    public DatabaseLabelAwareIterator(int limit, int offset) throws SQLException {
        this.limit = limit; this.offset = offset;
        setupMainConn();
        initializeQuery();
    }

    private void initializeQuery() throws SQLException {
        System.out.println("Starting query...");
        PreparedStatement ps = mainConn.prepareStatement(mainQuery);
        ps.setInt(1,limit);
        ps.setInt(2,offset);
        ps.setFetchSize(10);
        System.out.println(ps);
        innerIterator=null;
        if(results!=null)results.close();
        results = ps.executeQuery();
    }

    public void setupMainConn() throws SQLException {
        System.out.println("Setting up main connection...");
        mainConn = DriverManager.getConnection(patentDBUrl);
        mainConn.setAutoCommit(false);
    }

    @Override
    public String currentLabel() {
        return currentLabel;
    }

    @Override
    public List<String> currentLabels() {
        return Arrays.asList(new String[]{currentLabel});
    }


    @Override
    public String nextSentence() {
        if(innerIterator==null || !innerIterator.hasNext()) {
            setupIterator();
        }
        return innerIterator.next();
    }

    private void setupIterator() {
        try {
            results.next();
            String pubDocNumber = results.getString(1);
            List<String> toIterator = new LinkedList<>();
            currentLabel = pubDocNumber;
            for(String sentence : results.getString(2).split("\\.")) {
                if(processor!=null)sentence= processor.preProcess(sentence);
                toIterator.add(sentence);
            }
            innerIterator=toIterator.iterator();
        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("ERROR WITH SETUP ITERATOR");
        }
    }

    @Override
    public boolean hasNext() {
        try {
            if(results.isAfterLast()) return false;
            else if(innerIterator!=null && results.isLast()) return innerIterator.hasNext();
            else return true;
        } catch (SQLException sql) {
            sql.printStackTrace();
            return false;
        }
    }

    @Override
    public void reset() {
        try {
            initializeQuery();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
    }

    @Override
    public void finish() {
        try{
            mainConn.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public SentencePreProcessor getPreProcessor() {
        return processor;
    }

    @Override
    public void setPreProcessor(SentencePreProcessor preProcessor) {
        processor = preProcessor;
    }

}
