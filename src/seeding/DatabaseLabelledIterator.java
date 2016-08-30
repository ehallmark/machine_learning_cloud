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
    protected List<VocabWord> currentSentence;
    protected VocabCache<VocabWord> vocabCache;
    protected Set<String> stopWords;
    protected long lastTime;
    protected boolean shouldStem;
    public static Stemmer stem = new Stemmer();
    // used to tag each sequence with own Id

    public DatabaseLabelledIterator(VocabCache<VocabWord> vocabCache,Set<String> stopWords, boolean shouldStem) throws SQLException {
        this.vocabCache=vocabCache;
        this.stopWords=stopWords;
        this.shouldStem = shouldStem;
        if(this.stopWords==null)this.stopWords=new HashSet<>();
        preProcessor=(t)->t;
        resultSet = Database.selectRawPatents();
    }

    public DatabaseLabelledIterator(VocabCache<VocabWord> vocabCache, boolean shouldStem) throws SQLException {
        this(vocabCache,null,shouldStem);
    }


    public DatabaseLabelledIterator(boolean shouldStem) throws SQLException {
        this(null,shouldStem);
    }

    public DatabaseLabelledIterator() throws SQLException {
        this(null,false);
    }

    public void setVocabAndStopWords(VocabCache<VocabWord> vocab, Set<String> stopWords) {
        this.vocabCache=vocab;
        this.stopWords=stopWords;
    }



    @Override
    public synchronized boolean hasNextDocument() {

        try {
            // Check patent iterator
            while (resultSet.next()) {
                currentLabel = resultSet.getString(1).split("_")[0];
                String[] words = (String[]) resultSet.getArray(2).getArray();
                //System.out.println(Arrays.toString(words));
                if(words.length < Constants.MIN_WORDS_PER_SENTENCE) continue;
                assert words != null : "Words array from PG is NULL!";

                currentSentence = Arrays.asList(words).stream().filter(t->t!=null).map(w->shouldStem ? stem.stem(w) : w).map(t->t==null||t.isEmpty()||stopWords.contains(t)?null:(vocabCache==null?new VocabWord(1.0,t):vocabCache.tokenFor(t))).filter(w->w!=null).collect(Collectors.toList());

                /*if(vocabCache!=null) {
                    assert vocabCache.hasToken(currentLabel) : "Vocab does not have current label: "+currentLabel;
                    VocabWord label = vocabCache.tokenFor(currentLabel);
                    label.setSequencesCount(newSentences.size());
                    label.setElementFrequency(newSentences.size());
                }*/
                if (currentSentence!=null&&!currentSentence.isEmpty()&&!(currentSentence.size()<Constants.MIN_WORDS_PER_SENTENCE)) return true;
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
        return nextDocumentHelper();
    }

    private synchronized LabelledDocument nextDocumentHelper() {
        /*int currentCnt = cnt.getAndIncrement();
        if(currentCnt%1000==999) {
            long time = System.currentTimeMillis();
            System.out.println("Time to complete 1000 patents: "+new Double(time-lastTime)/(1000)+" seconds");
            lastTime = time;
            cnt.set(0);
        }*/
        LabelledDocument doc = new LabelledDocument();
        doc.setReferencedContent(currentSentence);
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
        cnt = new AtomicInteger(0);
        lastTime = System.currentTimeMillis();
    }

    @Override
    public LabelsSource getLabelsSource() {
        return null;
    }

}
