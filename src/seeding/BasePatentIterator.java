package seeding;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.VocabularyWord;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class BasePatentIterator implements SequenceIterator<VocabWord> {

    private final int startDate;
    private ResultSet resultSet;
    private VocabWord currentPatent;
    private Iterator<List<VocabWord>> currentPatentIterator;
    // used to tag each sequence with own Id
    protected AtomicInteger tagger = new AtomicInteger(0);

    public BasePatentIterator(int startDate) throws SQLException {
        this.startDate=startDate;
    }

    public void resetQuery() throws SQLException {
        resultSet = Database.getPatentVectorData(startDate);
    }

    protected void setCurrentPatent() throws SQLException {
        currentPatent = new VocabWord(2,resultSet.getString(1));
    }

    protected Iterator<List<VocabWord>> processedSentenceIterator() throws SQLException {
        List<List<VocabWord>> preIterator = new LinkedList<>();

        // Abstract
        String[] abstractText = (String[])resultSet.getArray(2).getArray();
        if(abstractText.length >= Constants.MIN_WORDS_PER_SENTENCE) {
            List<VocabWord> abstractWords = new ArrayList<>();
            for(String word : abstractText) {
                if(word!=null && word.length() > 0)abstractWords.add(new VocabWord(0, word));
            }
        }

        // Description
        String[] descriptionText = (String[])resultSet.getArray(3).getArray();
        if(descriptionText.length >= Constants.MIN_WORDS_PER_SENTENCE) {
            List<VocabWord> descriptionWords = new ArrayList<>();
            for(String word : descriptionText) {
                if(word!=null && word.length() > 0)descriptionWords.add(new VocabWord(0, word));
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
                sequence.setSequenceId(tagger.getAndIncrement());
                sequence.setSequenceLabel(currentPatent);
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
        tagger.set(0);
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
        currentPatent=null;
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
