package seeding;

import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
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
    protected Iterator<List<String>> currentSentenceIterator;
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
    public synchronized boolean hasNextDocument() {

        try {
            if(currentSentenceIterator!=null&&currentSentenceIterator.hasNext()) return true;
            // Check patent iterator
            while (resultSet.next()) {
                currentLabel = resultSet.getString(1).split("_")[0];
                String[] words = (String[]) resultSet.getArray(2).getArray();
                //System.out.println(Arrays.toString(words));
                if(words.length < Constants.MIN_WORDS_PER_SENTENCE) continue;
                assert words != null : "Words array from PG is NULL!";
                Iterator<String> current = Arrays.asList(words).iterator();
                List<List<String>> newSentences = new ArrayList<>();
                List<String> sentence = new ArrayList<>(Constants.MAX_WORDS_PER_DOCUMENT);
                List<String> oldBuffer = new ArrayList<>(Constants.SENTENCE_PADDING);
                List<String> newBuffer = new ArrayList<>(Constants.SENTENCE_PADDING);
                while(current.hasNext()) {
                    String word = current.next();
                    sentence.add(word);
                    if(sentence.size() >= Constants.MAX_WORDS_PER_DOCUMENT) {
                        for(int i = sentence.size()-Constants.SENTENCE_PADDING; i < sentence.size(); i++) {
                            newBuffer.add(sentence.get(i));
                        }
                        if(!oldBuffer.isEmpty()) {
                            sentence.addAll(0,oldBuffer);
                        }
                        newSentences.add(sentence);
                        oldBuffer = newBuffer;
                        newBuffer = new ArrayList<>(Constants.SENTENCE_PADDING);
                        sentence=new ArrayList<>(Constants.MAX_WORDS_PER_DOCUMENT);
                    }
                }
                currentSentenceIterator = newSentences.iterator();
                if (currentSentenceIterator.hasNext()) return true;
                System.out.println("RECURSIVE CALL!!!!!");
            }

        } catch (Exception sql) {
            sql.printStackTrace();
            return false;
        }
        return false;

    }

    @Override
    public LabelledDocument nextDocument() {
        return nextDocument(vocabCache);
    }

    public synchronized LabelledDocument nextDocument(VocabCache<VocabWord> vocab) {
        int currentCnt = cnt.getAndIncrement();
        if(currentCnt%1000==999) {
            long time = System.currentTimeMillis();
            System.out.println("Time to complete 1000 patents: "+new Double(time-lastTime)/(1000)+" seconds");
            lastTime = time;
            cnt.set(0);
        }
        LabelledDocument doc = new LabelledDocument();
        doc.setReferencedContent(currentSentenceIterator.next().stream().map(t->vocab==null ? new VocabWord(1.0,t) : vocabCache.tokenFor(t)).filter(w->w!=null).collect(Collectors.toList()));
        doc.setLabel(currentLabel);
        //System.out.println(currentLabel);
        //System.out.println(Arrays.toString(currentSentence.toArray()));
        return doc;
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
    public LabelsSource getLabelsSource() {
        return null;
    }

}
