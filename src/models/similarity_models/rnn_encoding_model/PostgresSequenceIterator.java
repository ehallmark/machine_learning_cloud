package models.similarity_models.rnn_encoding_model;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import seeding.google.postgres.Util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicLong;

public class PostgresSequenceIterator implements SequenceIterator<VocabWord> {
    private AtomicLong counter = new AtomicLong(0);
    private ResultSet rs;
    private long[] limits;
    private int iter;
    private final PreparedStatement ps;
    private final int textIdx;
    public PostgresSequenceIterator(PreparedStatement ps, int textIdx, long... limits) {
        this.ps=ps;
        this.limits=limits;
        this.iter=0;
        this.textIdx=textIdx;
    }

    private synchronized void newResultSet() {
        try {
            if (rs != null && !rs.isClosed()) {
                rs.close();
            }
        } catch(Exception e) {

        }
        try {
            ps.setFetchSize(10);
            this.rs = ps.executeQuery();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasMoreSequences() {
        if(limits.length>0) {
            long l = limits[Math.min(limits.length-1,iter)];
            if(l>0&&counter.get()>=l) {
                System.out.println("Limit reached.");
                return false;
            }
        }
        if(rs==null) {
            this.newResultSet();
        }
        try {
            return rs.next();
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Sequence<VocabWord> nextSequence() {
        try {
            String[] words = Util.textToWordFunction.apply(rs.getString(textIdx));
            Sequence<VocabWord> sequence = new Sequence<>();
            for(String word : words) {
                if(word==null||word.isEmpty()) continue;
                VocabWord vocabWord = new VocabWord(1f,word);
                vocabWord.setElementFrequency(1);
                vocabWord.setSequencesCount(1);
                sequence.addElement(vocabWord);
            }
            if(counter.getAndIncrement()%100000==99999) {
                System.out.println("Finished: "+counter.get());
            }
            return sequence;
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error creating sequence!");
            return new Sequence<>();
        }

    }

    @Override
    public void reset() {
        System.out.println("Resetting iterator...");
        iter++;
        this.newResultSet();
        counter.set(0);
    }
}
