package learning;

import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import seeding.Constants;
import semantic_hash.DatabaseLabelAwareIterator;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Created by ehallmark on 7/14/16.
 */
public class LearnAllWordVectors extends LearnCompdbWordVectors {

    @Override
    protected void initializeVariables() throws SQLException,IOException,ClassNotFoundException {
        paragraphVectorFile = new File(Constants.SEMANTIC_HASH_PARAGRAPH_VECTORS_FILE);
        vocabCacheFile = new File(Constants.SEMANTIC_HASH_VOCAB_CACHE_FILE);
        wordVectorFile = new File(Constants.SEMANTIC_HASH_WORD_VECTORS);
        // Check for paragraphVectors.obj file
        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(token->token);

        // build a iterator for our dataset
        iterator = new DatabaseLabelAwareIterator(100000,1000);

        numOuputs = 200;
    }

    @Override
    protected void setupLabelAwareIterator() {
        iterator.reset();
        labelAwareIterator = (LabelAwareSentenceIterator)iterator;
    }


    public static void main(String[] args) throws Exception {
        LearnAllWordVectors app = new LearnAllWordVectors();
        app.initializeVariables();
        app.loadOrCreateAndSaveVocabCache();
        app.loadOrCreateAndSaveWordVectors();
        app.loadOrCreateAndSaveParagraphVectors();
    }
}
