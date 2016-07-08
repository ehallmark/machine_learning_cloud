package learning;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import seeding.DatabaseIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import seeding.Constants;
import seeding.MyPreprocessor;

import java.io.*;
import java.sql.SQLException;
import java.util.Arrays;


/**
 * This is basic example for documents classification done with DL4j ParagraphVectors.
 * The overall idea is to use ParagraphVectors in the same way we use LDA:
 * topic space modelling.
 *
 * In this example we assume we have few labeled categories that we can use 
 * for training, and few unlabeled documents. And our goal is to determine, 
 * which category these unlabeled documents fall into
 *
 *
 * Please note: This example could be improved by using learning cascade 
 * for higher accuracy, but that's beyond basic example paradigm.
 *
 * @author raver119@gmail.com
 */
public class ClassifyPatentsDocVectors {
    private ParagraphVectors paragraphVectors;
    private LabelAwareIterator iterator;
    private TokenizerFactory tokenizerFactory;

    public static void main(String[] args) throws Exception {
      
        ClassifyPatentsDocVectors app = new ClassifyPatentsDocVectors();
        app.initializeVariables();
        app.makeParagraphVectors();
        app.startTraining();
        app.saveParagraphVectors();

    }

    private void initializeVariables() throws SQLException {
        // Check for paragraphVectors.obj file
        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
        // build a iterator for our dataset
        iterator = new DatabaseIterator(false);
    }

	private void saveParagraphVectors() throws IOException {
    	// dont overwrite
    	File pVectors = new File("paragraphVectors.txt");
        if(pVectors.exists())pVectors.delete();
        // Write word vectors
        WordVectorSerializer.writeWordVectors(paragraphVectors, new File("paragraphVectors.txt"));

    }

	void makeParagraphVectors()  throws Exception {

      // ParagraphVectors training configuration
      paragraphVectors = new ParagraphVectors.Builder()
              .learningRate(0.0001)
              .minLearningRate(0.00001)
              .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
              .epochs(50)
              .batchSize(((DatabaseIterator)iterator).numPatents())
              .windowSize(5)
              .iterations(5)
              .iterate(iterator)
              .layerSize(((DatabaseIterator)iterator).numTechnologies())
              .stopWords(Arrays.asList(Constants.STOP_WORDS))
              .trainWordVectors(true)
              .trainElementsRepresentation(true)
              .trainSequencesRepresentation(false)
              .tokenizerFactory(tokenizerFactory)
              .build();

	}

    public void startTraining() {
        // Start model training
        paragraphVectors.fit();
    }

}
