package seeding;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by ehallmark on 7/18/16.
 */
public class BasePatentIterator implements SequenceIterator<VocabWord> {

    private final int startDate;
    private ResultSet resultSet;
    private String currentPatent;
    private VocabCache<VocabWord> vocab;
    private SentencePreProcessor preProcessor;
    private Iterator<List<VocabWord>> currentPatentIterator;

    public BasePatentIterator(int startDate) throws SQLException {
        this.startDate=startDate;
        this.preProcessor = new MyPreprocessor();

        vocab = new AbstractCache.Builder<VocabWord>()
                .hugeModelExpected(true)
                .minElementFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .build();
        VocabConstructor<VocabWord> constructor = new VocabConstructor.Builder<VocabWord>()
                .setTargetVocabCache(vocab)
                .fetchLabels(true)
                .addSource(this, Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .setStopWords(Arrays.asList(Constants.STOP_WORDS))
                .build();

        constructor.buildJointVocabulary(true, false);
    }

    public void resetQuery() throws SQLException {
        resultSet = Database.getPatentVectorData(startDate);
    }

    /*@Override
    public String currentLabel() {
        return currentPatent;
    }

    @Override
    public List<String> currentLabels() {
        return Arrays.asList(currentPatent);
    }
    */

    protected void setCurrentPatent() throws SQLException {
        currentPatent = resultSet.getString(1);
    }

    protected Iterator<List<VocabWord>> processedSentenceIterator() throws SQLException {
        List<List<VocabWord>> preIterator = new LinkedList<>();

        // Abstract
        String[] abstractText = (String[])resultSet.getArray(2).getArray();
        if(abstractText.length >= Constants.MIN_WORDS_PER_SENTENCE) {
            List<VocabWord> abstractWords = new ArrayList<>();
            for(String word : abstractText) {
                if(word!=null && vocab.hasToken(word))abstractWords.add(vocab.tokenFor(word));
            }
        }

        // Description
        String[] descriptionText = (String[])resultSet.getArray(3).getArray();
        if(descriptionText.length >= Constants.MIN_WORDS_PER_SENTENCE) {
            List<VocabWord> descriptionWords = new ArrayList<>();
            for(String word : descriptionText) {
                if(word!=null && vocab.hasToken(word))descriptionWords.add(vocab.tokenFor(word));
            }
        }
        return preIterator.iterator();
    }


    public boolean shouldRemoveSentence(String str) {
        if(str==null)return true;
        boolean wasChar = false;
        int wordCount = 0;
        for(Character c : str.toCharArray()) {
            if(Character.isSpaceChar(c) && wasChar) {
                wordCount++;
                wasChar = false;
            } else if(Character.isAlphabetic(c)) {
                wasChar = true;
            }
            if(wordCount >= Constants.MIN_WORDS_PER_SENTENCE) return false;
        }
        return true;
    }

    @Override
    public boolean hasMoreSequences() {
        try {
            return ((currentPatentIterator == null || currentPatentIterator.hasNext()) || (resultSet == null || !(resultSet.isAfterLast() || resultSet.isLast())));
        } catch (SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR WHILE ITERATING");
        }
    }


    @Override
    public Sequence<VocabWord> nextSequence() {
        try {
            // Check patent iterator
            if(currentPatentIterator!=null && currentPatentIterator.hasNext()) {
                Sequence<VocabWord> sequence = new Sequence<>(currentPatentIterator.next());
                sequence.setSequenceLabel(new VocabWord(2,currentPatent));
                return sequence;
            }
            // Check for more results in result set
            resultSet.next();

            setCurrentPatent();
            currentPatentIterator = processedSentenceIterator();
            //  System.out.println("Number of sentences for "+currentPatent+": "+preIterator.size());
            return nextSequence();

        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR");
        }
    }

    @Override
    public void reset() {
        try {
            if(resultSet!=null && !resultSet.isClosed()) resultSet.close();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        try {
            resetQuery();
        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("UNABLE TO RESET QUERY");
        }
        currentPatentIterator=null;
    }

    /*@Override
    public void finish() {
        Database.close();
    }

    @Override
    public SentencePreProcessor getPreProcessor() {
        return preProcessor;
    }

    @Override
    public void setPreProcessor(SentencePreProcessor preProcessor) {
        this.preProcessor=preProcessor;
    }
    */

}
