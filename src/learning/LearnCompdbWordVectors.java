package learning;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

import org.deeplearning4j.models.embeddings.learning.ElementsLearningAlgorithm;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import seeding.Constants;


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
public class LearnCompdbWordVectors {
    private Word2Vec wordVectors;
    private ParagraphVectors paragraphVectors;
    private SentenceIterator iterator;
    private TokenizerFactory tokenizerFactory;
    private VocabCache<VocabWord> vocabCache;
    private int numClassifications;
    private int numTrainingInputs;

    private void initializeVariables() throws SQLException,IOException,ClassNotFoundException {
        // Check for paragraphVectors.obj file
        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(token->token);

        // build a iterator for our dataset
        iterator = new PatentIterator(new File(Constants.COMPDB_TRAIN_FOLDER), new File(Constants.COMPDB_TRAIN_LABEL_FILE));

        numClassifications = CountFiles.getNumberOfClassifications();
        numTrainingInputs = CountFiles.getNumberOfTrainingPatents();

    }

    private void loadOrCreateVocabCache() throws IOException {
        System.out.println("Starting to load vocab cache...");

        File vCache = new File(Constants.COMPDB_VOCAB_CACHE_FILE);
        if(vCache.exists()) {
            vocabCache = WordVectorSerializer.readVocabCache(vCache);
        } else {
            vocabCache = new AbstractCache.Builder<VocabWord>()
                    .hugeModelExpected(true)
                    .minElementFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                    .build();

            SentenceTransformer transformer = new SentenceTransformer.Builder()
                    .iterator(iterator)
                    .tokenizerFactory(tokenizerFactory)
                    .build();

            SequenceIterator<VocabWord> sequenceIterator = new AbstractSequenceIterator.Builder<>(transformer)
                    .build();

            VocabConstructor<VocabWord> constructor = new VocabConstructor.Builder<VocabWord>()
                    .setTargetVocabCache(vocabCache)
                    .setStopWords(Arrays.asList(Constants.STOP_WORDS))
                    .fetchLabels(false)
                    .addSource(sequenceIterator, Constants.DEFAULT_MIN_WORD_FREQUENCY)
                    .build();

            constructor.buildJointVocabulary(false, true);

        }
        System.out.println("Done loading vocab cache...");

    }

    private void saveVocabCache() throws IOException {
        // dont overwrite
        File vCache = new File(Constants.COMPDB_VOCAB_CACHE_FILE);
        if(!vCache.exists()) WordVectorSerializer.writeVocabCache(vocabCache, vCache);
    }

	private void saveWordVectors() throws IOException {
    	// dont overwrite
    	File pVectors = new File(Constants.COMPDB_WORD_VECTORS);
        if(!pVectors.exists())WordVectorSerializer.writeFullModel(wordVectors, pVectors.getAbsolutePath());
    }

	void loadOrCreateWordVectors()  throws Exception {
        System.out.println("Starting to load word vectors...");
        // ParagraphVectors training configuration
        File wordVectorFile = new File(Constants.COMPDB_WORD_VECTORS);

        if(!wordVectorFile.exists()) {
            wordVectors = new Word2Vec.Builder()
                    .learningRate(0.025)
                    .minLearningRate(0.001)
                    .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                    .epochs(5)
                    .batchSize(1000)
                    .iterations(3)
                    .useAdaGrad(false)
                    .negativeSample(10)
                    .windowSize(5)
                    .layerSize(numClassifications)
                    .stopWords(Arrays.asList(Constants.STOP_WORDS))
                    .iterate(iterator)
                    .vocabCache(vocabCache)
                    .resetModel(false)
                    .tokenizerFactory(tokenizerFactory)
                    .build();

            // Start training
            wordVectors.fit();
        } else {
            // load
            wordVectors = WordVectorSerializer.loadFullModel(wordVectorFile.getAbsolutePath());
        }
        System.out.println("Done loading word vectors...");
	}

    public void loadOrCreateParagraphVectors() throws IOException {
        System.out.println("Starting to load paragraph vectors...");

        File pFile = new File(Constants.COMPDB_PARAGRAPH_VECTORS);
        if(pFile.exists()) {
            paragraphVectors = WordVectorSerializer.readParagraphVectorsFromText(pFile);
            // Done
        } else {

            // Otherwise build from scratch
            if (wordVectors == null) throw new RuntimeException("Please have word vectors!!!");
            if (vocabCache == null) throw new RuntimeException("Please have vocab cache!!!");

            iterator = new ParagraphIterator(new File(Constants.COMPDB_TRAIN_FOLDER), new File(Constants.COMPDB_TRAIN_LABEL_FILE));

            paragraphVectors = new ParagraphBuilder()
                    .useWordVectors(wordVectors)
                    .trainElementsRepresentation(true)
                    .trainSequencesRepresentation(false)
                    .vocabCache(vocabCache)
                    .resetModel(false)
                    .tokenizerFactory(tokenizerFactory)
                    .layerSize(numClassifications)
                    .epochs(10)
                    .windowSize(5)
                    .batchSize(1000)
                    .iterate(iterator)
                    .stopWords(Arrays.asList(Constants.STOP_WORDS))
                    .iterations(3)
                    .negativeSample(10)
                    .learningRate(0.001)
                    .minLearningRate(0.0001)
                    .trainWordVectors(false)
                    .build();

            paragraphVectors.fit();

        }
        System.out.println("Done loading paragraph vectors...");

    }

    public void saveParagraphVectors() {
        File pFile = new File(Constants.COMPDB_PARAGRAPH_VECTORS);
        if(!pFile.exists()) WordVectorSerializer.writeWordVectors(paragraphVectors,pFile);
    }

    public static void main(String[] args) throws Exception {
        LearnCompdbWordVectors app = new LearnCompdbWordVectors();
        app.initializeVariables();
        app.loadOrCreateVocabCache();
        app.saveVocabCache();
        app.loadOrCreateWordVectors();
        app.saveWordVectors();
        app.loadOrCreateParagraphVectors();
        app.saveParagraphVectors();
    }

}
