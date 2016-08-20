package seeding;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import tools.VectorHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class DatabaseLabelledIterator implements LabelAwareSentenceIterator {

    protected SentencePreProcessor preProcessor;
    protected String currentLabel;
    protected AtomicInteger cnt;
    protected ResultSet resultSet;
    protected Iterator<String> sentenceIter;
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
        return sentenceIter.next();
    }


    @Override
    public boolean hasNext() {
        try {
            // Check patent iterator
            if(sentenceIter!=null && sentenceIter.hasNext()) return true;
            if(resultSet.next()) {
                currentLabel = resultSet.getString(1);
                sentenceIter = createSentencesFromLargeText(resultSet.getString(2)).iterator();
                return sentenceIter.hasNext();
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


    private List<String> createSentencesFromLargeText(String toBreakUp) {
        final int maxNumberOfWordsPerSentence = 20;
        String[] words = toBreakUp.split("\\s+");
        List<String> sentences = new ArrayList<>((words.length+1)/maxNumberOfWordsPerSentence);
        for(int i = 0; i < words.length-maxNumberOfWordsPerSentence; i+= maxNumberOfWordsPerSentence) {
            String sentence = String.join(" ",Arrays.copyOfRange(words,i,i+maxNumberOfWordsPerSentence));
            sentences.add(sentence);
            System.out.println(currentLabel+ " => "+sentence);

        }
        return sentences;
    }
}
