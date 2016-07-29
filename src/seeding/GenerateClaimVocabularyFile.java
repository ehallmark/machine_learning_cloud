package seeding;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import tools.WordVectorSerializer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by ehallmark on 7/28/16.
 */
public class GenerateClaimVocabularyFile {
    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            new GenerateVocabulary(new PatentClaimIterator(Constants.VOCAB_START_DATE));
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }
}
