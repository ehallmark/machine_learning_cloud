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
public class GenerateVocabularyFile {
    public GenerateVocabularyFile(File vocabFile, SentenceIterator sentenceIterator) throws Exception{
        // Create Iterator

        SentenceTransformer transformer = new SentenceTransformer.Builder()
                .readOnly(true)
                .tokenizerFactory(new DefaultTokenizerFactory())
                .iterator(sentenceIterator)
                .build();

        SequenceIterator<VocabWord> iterator = new AbstractSequenceIterator.Builder<>(transformer).build();

        // Start on Vocabulary
        System.out.println("Starting to build vocabulary...");
        WordVectorSerializer.writeVocabCache(buildAndWriteVocabulary(iterator), vocabFile);

        /*// Start on WordVectors
        System.out.println("Starting Word Vectors...");
        if (!wordVectorsFile.exists()) buildAndWriteParagraphVectors();
        else {
            wordVectors = WordVectorSerializer.loadFullModel(wordVectorsFile.getAbsolutePath());
        }*/
    }

    private VocabCache<VocabWord> buildAndWriteVocabulary(SequenceIterator<VocabWord> iterator) throws IOException {
        VocabCache<VocabWord> vocab = new AbstractCache.Builder<VocabWord>()
                .minElementFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .hugeModelExpected(true)
                .build();

        VocabConstructor<VocabWord> constructor = new VocabConstructor.Builder<VocabWord>()
                .setTargetVocabCache(vocab)
                .setStopWords(Arrays.asList(Constants.STOP_WORDS))
                .fetchLabels(false)
                .addSource(iterator, Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .build();

        constructor.buildJointVocabulary(false, true);
        vocab.saveVocab();
        return vocab;
    }

    /*private void buildAndWriteParagraphVectors() throws IOException {
        WeightLookupTable<VocabWord> lookupTable = new InMemoryLookupTable.Builder<VocabWord>()
                .useAdaGrad(false)
                .vectorLength(Constants.VECTOR_LENGTH)
                .seed(41)
                .cache(vocab)
                .build();

        lookupTable.resetWeights(true);

        Word2Vec wordVectors = new Word2Vec.Builder()
                .seed(41)
                .useAdaGrad(false)
                .resetModel(false)
                .batchSize(1000)
                .epochs(1)
                .vocabCache(vocab)
                .lookupTable(lookupTable)
                .iterations(3)
                .windowSize(10)
                .iterate(iterator)
                .layerSize(Constants.VECTOR_LENGTH)
                .stopWords(Arrays.asList(Constants.STOP_WORDS))
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .learningRate(0.0025)
                .minLearningRate(0.0001)
                .sampling(0.0001)
                .build();

        wordVectors.fit();

        WordVectorSerializer.writeFullModel((Word2Vec)wordVectors, wordVectorsFile.getAbsolutePath());
    }*/

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            new GenerateVocabularyFile(new File(Constants.VOCAB_FILE), new BasePatentIterator(Constants.VOCAB_START_DATE));
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }
}
