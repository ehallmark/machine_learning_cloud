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
import java.util.List;
import java.util.Arrays;

/**
 * Created by ehallmark on 6/21/16.
 */
public class DatabaseLabelAwareIterator implements LabelAwareSentenceIterator {

    private String patentDBUrl = "jdbc:postgresql://localhost/patentdb?user=postgres&password=&tcpKeepAlive=true";
    private Connection mainConn;
    private ResultSet results;
    private List<String> labels;
    private int limit;
    private int offset;
    private String currentLabel;
    private SentencePreProcessor processor;
    private final String mainQuery = "SELECT pub_doc_number,array_to_string(word_array(coalesce(abstract,'')), ' ') as abstract FROM patent_grant ORDER BY pub_doc_number LIMIT ? OFFSET ?";
    private final String labelQuery = "SELECT pub_doc_number FROM patent_grant ORDER BY pub_doc_number LIMIT ? OFFSET ?";

    public DatabaseLabelAwareIterator(int limit, int offset) throws SQLException {
        this.limit = limit; this.offset = offset;
        setupMainConn();
        setupLabels();
        initializeQuery();
        this.setPreProcessor(new SentencePreProcessor() {
            @Override
            public String preProcess(String sentence) {
                return sentence;
            }
        });
    }

    public void initializeQuery() throws SQLException {
        System.out.println("Starting query...");
        PreparedStatement ps = mainConn.prepareStatement(mainQuery);
        ps.setInt(1,limit);
        ps.setInt(2,offset);
        ps.setFetchSize(10);
        results = ps.executeQuery();
    }

    public void setupMainConn() throws SQLException {
        System.out.println("Setting up main connection...");
        mainConn = DriverManager.getConnection(patentDBUrl);
        mainConn.setAutoCommit(false);
    }

    public void setupLabels() throws SQLException {
        System.out.println("Setting up labels...");
        PreparedStatement getLabels = mainConn.prepareStatement("SELECT array_agg(pub_doc_number) as labels FROM ("+labelQuery+") as temp");
        getLabels.setInt(1,limit);
        getLabels.setInt(2,offset);
        ResultSet labelResults = getLabels.executeQuery();
        labelResults.next();
        labels = Arrays.asList((String[])labelResults.getArray(1).getArray());
        System.out.println("Completed Labels...");

    }

    //@Override
    public boolean hasNextDocument() {
        try {
            return !(results.isLast()||results.isAfterLast());
        } catch(SQLException sql) {
            sql.printStackTrace();
            return false;
        }
    }

    @Override
    public String currentLabel() {
        return currentLabel;
    }

    @Override
    public List<String> currentLabels() {
        return labels;
    }


    @Override
    public String nextSentence() {
        return nextDocument().getContent();
    }

    @Override
    public boolean hasNext() {
        return hasNextDocument();
    }

    public LabelledDocument nextDocument() {
        try {
            results.next();
            currentLabel=results.getString(1);
            LabelledDocument doc = new LabelledDocument();
            doc.setContent(results.getString(2));
            doc.setLabel(currentLabel);
            if(doc.getContent()==null) return nextDocument();
            return doc;
        } catch (SQLException sql) {
            sql.printStackTrace();
            return null;
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


    //@Override
    public LabelsSource getLabelsSource() {
        return new LabelsSource() {
            @Override
            public List<String> getLabels() {
                return labels;
            }
        };
    }

}
