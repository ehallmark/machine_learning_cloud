package seeding;

import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import tools.Emailer;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.SQLException;

/**
 * Created by ehallmark on 9/1/16.
 */
public class BuildVocabulary {
    public static void main(String[] args) throws SQLException {
        Database.setupSeedConn();
        DatabaseLabelledIterator iterator = new DatabaseLabelledIterator();
        System.out.println("Setting up iterator...");
        File vocabFile = new File(Constants.VOCAB_FILE);
        AbstractSequenceIterator<VocabWord> sequenceIterator = BuildParagraphVectors.createSequenceIterator(iterator);

        System.out.println("Starting on vocab building...");


        VocabCache<VocabWord> vocabCache = new AbstractCache.Builder<VocabWord>()
                .hugeModelExpected(false)
                .minElementFrequency(Constants.MIN_WORDS_PER_SENTENCE)
                .build();

                /*
                    Now we should build vocabulary out of sequence iterator.
                    We can skip this phase, and just set AbstractVectors.resetModel(TRUE), and vocabulary will be mastered internally
                */
        VocabConstructor<VocabWord> constructor = new VocabConstructor.Builder<VocabWord>()
                .addSource(sequenceIterator, Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .setTargetVocabCache(vocabCache)
                .build();

        constructor.buildJointVocabulary(false, true);

        WordVectorSerializer.writeVocab(vocabCache, vocabFile);
        System.out.println("Vocabulary finished...");
        new Emailer("Finished vocabulary!");
        Database.close();
    }
}
