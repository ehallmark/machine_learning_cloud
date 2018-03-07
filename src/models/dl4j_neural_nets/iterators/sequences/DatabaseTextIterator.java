package models.dl4j_neural_nets.iterators.sequences;

/**
 * Created by ehallmark on 11/16/16.
 */

import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by ehallmark on 7/18/16.
 */
public class DatabaseTextIterator implements LabelAwareSentenceIterator {

    protected Connection databaseConn;
    protected PreparedStatement statement;
    protected String query;
    protected List<String> currentLabels;
    protected List<Integer> labelIndices;
    protected List<Integer> textIndices;
    protected List<Integer> labelArrayIndices;
    protected LinkedList<Document> documentQueue;
    protected ResultSet resultSet;
    protected final int seekDistance = 1000;

    // used to tag each sequence with own Id
    private DatabaseTextIterator(String query, String databaseURL) throws SQLException {
        this.query = query;
        databaseConn = DriverManager.getConnection(databaseURL);
        statement=databaseConn.prepareStatement(query);
        labelIndices = new ArrayList<>();
        textIndices = new ArrayList<>();
        labelArrayIndices = new ArrayList<>();
        documentQueue = new LinkedList<>();
    }

    public void init() throws SQLException {
        resultSet = statement.executeQuery();
        System.out.println(statement.toString());
    }

    public void getMoreResults() {
        if(documentQueue.size()>=seekDistance/2)return;
        try {
            int counter = 0;
            // Check patent iterator
            while (resultSet.next()&&counter<seekDistance) {
                List<String> labels = new ArrayList<>();
                for (int i : labelArrayIndices) {
                    labels.addAll(Arrays.asList((String[]) resultSet.getArray(i).getArray()));
                }
                for (int i : labelIndices) {
                    labels.add(resultSet.getString(i));
                }
                for (int i : textIndices) {
                    String text = resultSet.getString(i);
                    if (text == null) continue;
                    documentQueue.add(new Document(labels, text));
                }
                counter++;
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String nextSentence() {
        Document doc = documentQueue.removeLast();
        currentLabels = doc.labels;
        return doc.words;
    }

    @Override
    public boolean hasNext() {
        getMoreResults();
        return documentQueue.size()>0;
    }

    @Override
    public void reset() {
        try {
            init();
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void finish() {

    }

    @Override
    public SentencePreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public void setPreProcessor(SentencePreProcessor preProcessor) {

    }

    @Override
    public String currentLabel() {
        return currentLabels().get(0);
    }

    @Override
    public List<String> currentLabels() {
        return currentLabels;
    }

    public static class Builder {
        private DatabaseTextIterator databaseTextIterator;
        public Builder(String query, String databaseURL) throws SQLException {
            databaseTextIterator = new DatabaseTextIterator(query, databaseURL);
        }
        public Builder setParameterAsInt(int paramIdx, int param) throws SQLException {
            databaseTextIterator.statement.setInt(paramIdx, param);
            return this;
        }
        public Builder setParameterAsString(int paramIdx, String param) throws SQLException {
            databaseTextIterator.statement.setString(paramIdx, param);
            return this;
        }
        public Builder setParameterAsArray(int paramIdx, Object[] params, String sqlType) throws SQLException {
            databaseTextIterator.statement.setArray(paramIdx, databaseTextIterator.databaseConn.createArrayOf(sqlType,params));
            return this;
        }
        public Builder setParameterAsBool(int paramIdx, boolean param) throws SQLException {
            databaseTextIterator.statement.setBoolean(paramIdx, param);
            return this;
        }
        public Builder setFetchSize(int fetchSize) throws SQLException {
            if(fetchSize<=0) {
                if(!databaseTextIterator.databaseConn.getAutoCommit()) databaseTextIterator.databaseConn.setAutoCommit(true);
            } else {
                if(databaseTextIterator.databaseConn.getAutoCommit()) databaseTextIterator.databaseConn.setAutoCommit(false);
                databaseTextIterator.statement.setFetchSize(fetchSize);
            }
            return this;
        }
        public Builder addLabelIndex(int idx) {
            databaseTextIterator.labelIndices.add(idx);
            return this;
        }
        public Builder setSeed(double seed) throws SQLException {
            PreparedStatement ps = databaseTextIterator.databaseConn.prepareStatement("select setseed(?)");
            ps.setDouble(1,seed);
            ps.executeQuery();
            ps.close();
            return this;
        }
        public Builder addLabelArrayIndex(int idx) {
            databaseTextIterator.labelArrayIndices.add(idx);
            return this;
        }
        public Builder addTextIndex(int idx) {
            databaseTextIterator.textIndices.add(idx);
            return this;
        }
        public DatabaseTextIterator build() throws SQLException {
            databaseTextIterator.init();
            return databaseTextIterator;
        }
    }

    private class Document {
        private List<String> labels;
        private String words;
        private Document(List<String> labels, String words) {
            this.labels=labels;
            this.words=words;
        }
    }

}
