package dl4j_neural_nets.iterators.sequences;

import dl4j_neural_nets.tools.DuplicatableSequence;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import seeding.Constants;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/2/16.
 */
public class DatabaseSequenceIterator implements SequenceIterator<VocabWord> {


    protected Connection databaseConn;
    protected PreparedStatement statement;
    protected String query;
    protected List<Integer> labelIndices;
    protected List<Integer> textIndices;
    protected boolean firstRunThrough = true;
    protected List<Integer> labelArrayIndices;
    protected LinkedList<Sequence<VocabWord>> documentQueue;
    protected ResultSet resultSet;
    protected final int seekDistance = 100;
    protected int numEpochs = 1;
    protected AtomicInteger epochCounter = new AtomicInteger(0);
    protected AtomicInteger sequenceCounter;

    // used to tag each sequence with own Id
    private DatabaseSequenceIterator(String query, String databaseURL) throws SQLException {
        this.query = query;
        databaseConn = DriverManager.getConnection(databaseURL);
        statement=databaseConn.prepareStatement(query);
        labelIndices = new ArrayList<>();
        textIndices = new ArrayList<>();
        labelArrayIndices = new ArrayList<>();
        documentQueue = new LinkedList<>();
        sequenceCounter = new AtomicInteger(0);
    }

    public void init() throws SQLException {
        resultSet = statement.executeQuery();
        sequenceCounter.set(0);
        System.out.println(statement.toString());
    }

    public void getMoreResults() {
        if(documentQueue.size()>=seekDistance/2)return;
        try {
            int counter = 0;
            // Check patent iterator
            while (!resultSet.isClosed()&&resultSet.next()&&counter<seekDistance) {
                List<String> labels = new ArrayList<>();
                for (int i : labelArrayIndices) {
                    labels.addAll(Arrays.asList((String[]) resultSet.getArray(i).getArray()));
                }
                for (int i : labelIndices) {
                    labels.add(resultSet.getString(i));
                }
                for (int i : textIndices) {
                    String[] text = (String[]) resultSet.getArray(i).getArray();
                    if (text == null) continue;
                    List<VocabWord> words = Arrays.stream(text)
                            .filter(word->!Constants.CLAIM_STOP_WORD_SET.contains(word))
                            .map(word -> new VocabWord(1.0, word))
                            .collect(Collectors.toList());
                    DuplicatableSequence<VocabWord> seq = new DuplicatableSequence<>(words);
                    for (int s = 0; s < labels.size(); s++) {
                        String label = labels.get(s);
                        VocabWord labelledWord = new VocabWord(1.0, label);
                        labelledWord.setSpecial(true);
                        Sequence<VocabWord> dupSeq;
                        if (s > 0 && firstRunThrough) {
                            dupSeq = new Sequence<>();
                        } else {
                            dupSeq = seq.dup();
                        }

                        dupSeq.setSequenceLabel(labelledWord);
                        documentQueue.add(dupSeq);
                    }
                }
                counter++;
            }

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Sequence<VocabWord> nextSequence() {
        if(sequenceCounter.get()%100000==0) {
            System.out.println("Line number: "+sequenceCounter.get());
        }
        sequenceCounter.getAndIncrement();
        return documentQueue.removeFirst();
    }

    @Override
    public void reset() {
        try {
            epochCounter.set(0);
            init();
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public static class Builder {
        private DatabaseSequenceIterator databaseIterator;
        public Builder(String query, String databaseURL) throws SQLException {
            databaseIterator = new DatabaseSequenceIterator(query, databaseURL);
        }
        public Builder setParameterAsInt(int paramIdx, int param) throws SQLException {
            databaseIterator.statement.setInt(paramIdx, param);
            return this;
        }
        public Builder setParameterAsString(int paramIdx, String param) throws SQLException {
            databaseIterator.statement.setString(paramIdx, param);
            return this;
        }
        public Builder setParameterAsArray(int paramIdx, Object[] params, String sqlType) throws SQLException {
            databaseIterator.statement.setArray(paramIdx, databaseIterator.databaseConn.createArrayOf(sqlType,params));
            return this;
        }
        public Builder setParameterAsBool(int paramIdx, boolean param) throws SQLException {
            databaseIterator.statement.setBoolean(paramIdx, param);
            return this;
        }
        public Builder setSeed(double seed) throws SQLException {
            PreparedStatement ps = databaseIterator.databaseConn.prepareStatement("select setseed(?)");
            ps.setDouble(1,seed);
            ps.executeQuery();
            ps.close();
            return this;
        }
        public Builder setFetchSize(int fetchSize) throws SQLException {
            if(fetchSize<=0) {
                if(!databaseIterator.databaseConn.getAutoCommit()) databaseIterator.databaseConn.setAutoCommit(true);
            } else {
                if(databaseIterator.databaseConn.getAutoCommit()) databaseIterator.databaseConn.setAutoCommit(false);
                databaseIterator.statement.setFetchSize(fetchSize);
            }
            return this;
        }
        public Builder setNumEpochs(int epochs) {
            databaseIterator.numEpochs=epochs;
            return this;
        }
        public Builder addLabelIndex(int idx) {
            databaseIterator.labelIndices.add(idx);
            return this;
        }
        public Builder addLabelArrayIndex(int idx) {
            databaseIterator.labelArrayIndices.add(idx);
            return this;
        }
        public Builder addTextIndex(int idx) {
            databaseIterator.textIndices.add(idx);
            return this;
        }
        public DatabaseSequenceIterator build() throws SQLException {
            databaseIterator.init();
            return databaseIterator;
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

    @Override
    public boolean hasMoreSequences() {
        getMoreResults();
        if(documentQueue.size()==0) {
            if(firstRunThrough) {
                // for vocab
                firstRunThrough=false;
                return false;
            }
            if(epochCounter.incrementAndGet()<numEpochs) {
                // re init
                try {
                    init();
                } catch(Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                getMoreResults();
            }
        }
        return documentQueue.size()>0;
    }

}
