package seeding;

import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/18/16.
 */
public class DatabaseLabelledIterator implements LabelAwareIterator {

    protected SentencePreProcessor preProcessor;
    protected String currentLabel;
    protected AtomicInteger cnt;
    protected ResultSet resultSet;
    protected Iterator<List<String>> sentenceIter;
    protected VocabCache<VocabWord> vocabCache;
    protected long lastTime;
    // used to tag each sequence with own Id

    public DatabaseLabelledIterator(VocabCache<VocabWord> vocabCache) throws SQLException {
        this.vocabCache=vocabCache;
        preProcessor=(t)->t;
        resultSet = Database.selectRawPatents();
    }

    public DatabaseLabelledIterator() throws SQLException {
        this(null);
    }



    @Override
    public boolean hasNextDocument() {
        try {
            // Check patent iterator
            if(sentenceIter!=null && sentenceIter.hasNext()) return true;
            if(resultSet.next()) {
                currentLabel = resultSet.getString(1);
                System.out.println(currentLabel);
                String[] words = (String[])resultSet.getArray(2).getArray();
                System.out.println(Arrays.toString(words));
                sentenceIter = createSentencesFromLargeText(Arrays.asList(words)).iterator();
                if(sentenceIter!=null&&sentenceIter.hasNext()) return true;
                return hasNextDocument();// Recursive
            }

        } catch(Exception sql) {
            sql.printStackTrace();
            return false;
        }
        return false;
    }

    @Override
    public LabelledDocument nextDocument() {
        return nextDocument(vocabCache);
    }

    public LabelledDocument nextDocument(VocabCache<VocabWord> vocab) {
        int currentCnt = cnt.getAndIncrement();
        if(currentCnt%1000==999) {
            long time = System.currentTimeMillis();
            System.out.println("Time to complete 1000 patents: "+new Double(time-lastTime)/(1000)+" seconds");
            lastTime = time;
            cnt.set(0);
        }
        List<String> nextTokens = sentenceIter.next();
        LabelledDocument doc = new LabelledDocument();
        doc.setReferencedContent(nextTokens.stream().map(t->vocab==null ? new VocabWord(1.0,t) : vocabCache.tokenFor(t)).collect(Collectors.toList()));
        doc.setLabel(currentLabel);
        return doc;
    }

    @Override
    public void reset() {
        try {
            resultSet = Database.selectRawPatents();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        sentenceIter=null;
        cnt = new AtomicInteger(0);
        lastTime = System.currentTimeMillis();
    }

    @Override
    public LabelsSource getLabelsSource() {
        return null;
    }

    private List<List<String>> createSentencesFromLargeText(List<String> wordList) {
        if(wordList==null||wordList.isEmpty()) return null;
        final int maxNumberOfWordsPerSentence = Math.min(20,wordList.size());
        List<List<String>> sentences = new ArrayList<>((wordList.size()+1)/maxNumberOfWordsPerSentence);
        int start = 0;
        while(start < sentences.size()) {
            sentences.add(wordList.subList(start, Math.min(start+maxNumberOfWordsPerSentence,wordList.size())));
            start+=maxNumberOfWordsPerSentence;
        }

        return sentences;
    }
}
