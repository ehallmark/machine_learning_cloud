package semantic_hash;

import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
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
    private List<String> labels;
    private int startDate;
    private int endDate;
    private String currentLabel;
    private SentencePreProcessor processor;
    private Iterator<LabelledDocument> innerIterator;
    private final String mainQuery = "SELECT pub_doc_number, invention_title, abstract FROM patent_grant WHERE ((invention_title is not null) AND (abstract is not null)) AND pub_date BETWEEN ? AND ?";

    public DatabaseLabelAwareIterator(int startDate, int endDate) throws SQLException {
        this.startDate = startDate; this.endDate = endDate; this.labels=new LinkedList<>();
        setupMainConn();
        initializeQuery();
    }

    private void initializeQuery() throws SQLException {
        System.out.println("Starting query...");
        PreparedStatement ps = mainConn.prepareStatement(mainQuery);
        ps.setInt(1,startDate);
        ps.setInt(2,endDate);
        ps.setFetchSize(10);
        System.out.println(ps);
        results = ps.executeQuery();
        labels = new LinkedList<>();
        while(results.next()) {
            String pubDocNumber = results.getString(1);
            labels.add(pubDocNumber);
            System.out.println(results.getString(2));
        }
    }

    public void setupMainConn() throws SQLException {
        System.out.println("Setting up main connection...");
        mainConn = DriverManager.getConnection(patentDBUrl);
        mainConn.setAutoCommit(false);
    }

    public List<String> getLabels() {
        return labels;
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
        LabelledDocument doc = innerIterator.next();
        currentLabel=doc.getLabel();
        System.out.println(doc.getContent());
        return doc.getContent();
    }

    private void setupIterator() {
        try {
            results.next();
            String pubDocNumber = results.getString(1);
            List<LabelledDocument> toIterator = new LinkedList<>();
            LabelledDocument titleDoc = new LabelledDocument();
            titleDoc.setLabel(pubDocNumber);
            titleDoc.setContent(results.getString(2).replaceAll(".",""));
            toIterator.add(titleDoc);
            labels.add(pubDocNumber);
            for(String sentence : results.getString(3).trim().split(".")) {
                LabelledDocument abstractDoc = new LabelledDocument();
                abstractDoc.setLabel(pubDocNumber);
                abstractDoc.setContent(sentence);
                toIterator.add(abstractDoc);
                labels.add(pubDocNumber);
            }
            innerIterator=toIterator.iterator();
            System.out.println("SETUP ITERATOR COMPLETE");
        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("ERROR WITH SETUP ITERATOR");
        }
    }

    @Override
    public boolean hasNext() {
        try {
            if(results.isAfterLast()) return false;
            else if(results.isLast() && innerIterator!=null) return innerIterator.hasNext();
            else return true;
        } catch (SQLException sql) {
            sql.printStackTrace();
            return false;
        }
    }

    @Override
    public void reset() {
        try {
            innerIterator=null;
            labels=new LinkedList<>();
            if(results!=null)results.close();
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
