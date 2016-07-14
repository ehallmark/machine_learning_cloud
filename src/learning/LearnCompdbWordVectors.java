package learning;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import tools.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
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
    protected Word2Vec wordVectors;
    protected ParagraphVectors paragraphVectors;
    protected SentenceIterator iterator;
    protected LabelAwareSentenceIterator labelAwareIterator;
    protected TokenizerFactory tokenizerFactory;
    protected VocabCache<VocabWord> vocabCache;
    protected int numOuputs;
    protected File paragraphVectorFile;
    protected File vocabCacheFile;
    protected File wordVectorFile;

    protected void initializeVariables() throws SQLException,IOException,ClassNotFoundException {
        paragraphVectorFile = new File(Constants.COMPDB_PARAGRAPH_VECTORS);
        vocabCacheFile = new File(Constants.COMPDB_VOCAB_CACHE_FILE);
        wordVectorFile = new File(Constants.COMPDB_WORD_VECTORS);

        // Check for paragraphVectors.obj file
        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(token->token);

        // build a iterator for our dataset
        iterator = new PatentIterator(new File(Constants.COMPDB_TRAIN_FOLDER), new File(Constants.COMPDB_TRAIN_LABEL_FILE));

        numOuputs = 200;
    }

    protected void loadOrCreateAndSaveVocabCache() throws IOException {
        System.out.println("Starting to load vocab cache...");

        if(vocabCacheFile.exists()) {
            vocabCache = WordVectorSerializer.readVocabCache(vocabCacheFile);
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
            saveVocabCache();

        }
        System.out.println("Done loading vocab cache...");

    }

    protected void saveVocabCache() throws IOException {
        // dont overwrite
        if(!vocabCacheFile.exists()) WordVectorSerializer.writeVocabCache(vocabCache, vocabCacheFile);
    }

    protected void saveWordVectors() throws IOException {
    	// dont overwrite
        WordVectorSerializer.writeFullModel(wordVectors, wordVectorFile.getAbsolutePath());
    }

    protected void loadOrCreateAndSaveWordVectors()  throws Exception {
        System.out.println("Starting to load word vectors...");
        // ParagraphVectors training configuration
        if(!wordVectorFile.exists()) {
            wordVectors = new Word2Vec.Builder()
                    .learningRate(0.025)
                    .minLearningRate(0.001)
                    .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                    .epochs(1)
                    .batchSize(1000)
                    .seed(41)
                    .iterations(3)
                    .useAdaGrad(false)
                    .negativeSample(10)
                    .windowSize(5)
                    .layerSize(numOuputs)
                    .stopWords(Arrays.asList(Constants.STOP_WORDS))
                    .iterate(iterator)
                    .vocabCache(vocabCache)
                    .resetModel(false)
                    .tokenizerFactory(tokenizerFactory)
                    .build();

            // Start training
            wordVectors.fit();
            saveWordVectors();
        }
        wordVectors = WordVectorSerializer.loadFullModel(wordVectorFile.getAbsolutePath());
        System.out.println("Done loading word vectors...");
	}

    protected void setupLabelAwareIterator() throws IOException {
        labelAwareIterator = new ParagraphIterator(new File(Constants.COMPDB_TRAIN_FOLDER), new File(Constants.COMPDB_TRAIN_LABEL_FILE));
    }

    protected void loadOrCreateAndSaveParagraphVectors() throws IOException {
        System.out.println("Starting to load paragraph vectors...");

        if(!paragraphVectorFile.exists()) {
            // Otherwise build from scratch
            if (wordVectors == null) throw new RuntimeException("Please have word vectors!!!");
            if (vocabCache == null) throw new RuntimeException("Please have vocab cache!!!");

            setupLabelAwareIterator();

            paragraphVectors = new ParagraphBuilder()
                    .useWordVectors(wordVectors)
                    .trainElementsRepresentation(false)
                    .trainSequencesRepresentation(true)
                    .vocabCache(vocabCache)
                    .resetModel(false)
                    .seed(41)
                    .tokenizerFactory(tokenizerFactory)
                    .layerSize(numOuputs)
                    .epochs(10)
                    .vocabCache(vocabCache)
                    .windowSize(5)
                    .batchSize(1000)
                    .iterate(labelAwareIterator)
                    .stopWords(Arrays.asList(Constants.STOP_WORDS))
                    .iterations(3)
                    .negativeSample(10)
                    .learningRate(0.001)
                    .minLearningRate(0.0001)
                    .trainWordVectors(false)
                    .build();

            paragraphVectors.fit();
            saveParagraphVectors();

        }
        paragraphVectors = WordVectorSerializer.readParagraphVectorsFromText(paragraphVectorFile);
        System.out.println("Done loading paragraph vectors...");

    }

    protected void saveParagraphVectors() {
        WordVectorSerializer.writeWordVectors(paragraphVectors,paragraphVectorFile);
    }

    public static void main(String[] args) throws Exception {
        LearnCompdbWordVectors app = new LearnCompdbWordVectors();
        app.initializeVariables();
        app.loadOrCreateAndSaveVocabCache();
        app.loadOrCreateAndSaveWordVectors();
        app.loadOrCreateAndSaveParagraphVectors();
    }

}
