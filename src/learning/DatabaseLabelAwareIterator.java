package learning;

import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import seeding.MyPreprocessor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by ehallmark on 6/21/16.
 */
public class DatabaseLabelAwareIterator implements LabelAwareIterator, SentenceIterator {

    private String patentDBUrl = "jdbc:postgresql://192.168.1.148/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private Connection mainConn;
    private SentencePreProcessor sentencePreprocessor;
    private ResultSet results;
    private int limit;
    private int offset;
    private LabelsSource source;
    private TokenPreProcess processor;
    private Iterator<LabelledDocument> innerIterator;
    private final String mainQuery = "SELECT pub_doc_number, abstract FROM patent_grant WHERE abstract is not null AND pub_date BETWEEN to_char(now()::date, 'YYYYMMDD')::int-150000 AND to_char(now()::date, 'YYYYMMDD')::int order by pub_doc_number desc limit ? offset ?";
    public DatabaseLabelAwareIterator(int limit, int offset) throws SQLException {
        this.limit = limit; this.offset = offset; this.processor=new MyPreprocessor();
        setupMainConn();
        reset();
        setupLabelsSource();
        reset();
    }


    private void setupLabelsSource() {
        List<String> labels = new LinkedList<>();
        while(hasNextDocument()) {
            LabelledDocument doc = nextDocument();
            if(!labels.contains(doc.getLabel()))labels.add(doc.getLabel());
            if(doc.getLabel()==null) throw new RuntimeException("NULL!!!!!!!!!");
        }
        source = new LabelsSource(labels);
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
    public LabelledDocument nextDocument() {
        if(innerIterator==null || !innerIterator.hasNext()) {
            setupIterator();
        }
        return innerIterator.next();
    }

    private void setupIterator() {
        try {
            results.next();
            String pubDocNumber = results.getString(1);
            List<LabelledDocument> toIterator = new LinkedList<>();
            for(String sentence : results.getString(2).split("\\.")) {
                if(processor!=null)sentence=processor.preProcess(sentence);
                LabelledDocument doc = new LabelledDocument();
                doc.setContent(sentence);
                doc.setLabel(pubDocNumber);
                toIterator.add(doc);
            }
            innerIterator=toIterator.iterator();
        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("ERROR WITH SETUP ITERATOR");
        }
    }

    @Override
    public boolean hasNextDocument() {
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
    public String nextSentence() {
        return nextDocument().getContent();
    }

    @Override
    public boolean hasNext() {
        return hasNextDocument();
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
        try {
            mainConn.close();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
    }

    @Override
    public SentencePreProcessor getPreProcessor() {
        return sentencePreprocessor;
    }

    @Override
    public void setPreProcessor(SentencePreProcessor preProcessor) {
        this.sentencePreprocessor = preProcessor;
    }

    @Override
    public LabelsSource getLabelsSource() {
        return source;
    }

}
