package seeding;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import tools.VectorHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class DatabaseLabelledIterator implements LabelAwareSentenceIterator {

    protected SentencePreProcessor preProcessor;
    protected String currentLabel;
    protected String currentSentence;
    protected AtomicInteger cnt;
    protected ResultSet resultSet;
    protected long lastTime;
    // used to tag each sequence with own Id

    public DatabaseLabelledIterator() throws SQLException {
        preProcessor=(t)->t;
        resultSet = Database.selectRawPatents();
    }

    @Override
    public String nextSentence() {
        int currentCnt = cnt.getAndIncrement();
        if(currentCnt%1000==999) {
            long time = System.currentTimeMillis();
            System.out.println("Time to complete 1000 patents: "+new Double(time-lastTime)/(1000)+" seconds");
            lastTime = time;
            cnt.set(0);
        }
        return currentSentence;
    }


    @Override
    public boolean hasNext() {
        try {
            // Check patent iterator
            if(resultSet.next()) {
                currentLabel = resultSet.getString(1);
                currentSentence = resultSet.getString(2);
                return true;
            }

        } catch(Exception sql) {
            sql.printStackTrace();
            return false;
        }
        return false;
    }

    @Override
    public void reset() {
        try {
            resultSet = Database.selectRawPatents();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        cnt = new AtomicInteger(0);
        lastTime = System.currentTimeMillis();
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
        return currentLabel;
    }

    @Override
    public List<String> currentLabels() {
        return null;
    }
}
