package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.learning.ElementsLearningAlgorithm;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import tools.WordVectorSerializer;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedPatentVectors {
    private int date;
    private static final File wordVectorsFile = new File(Constants.WORD_VECTORS_PATH);
    private static final File vocabFile = new File(Constants.VOCAB_FILE);
    private Word2Vec wordVectors;
    private SequenceIterator<VocabWord> iterator;
    private VocabCache<VocabWord> vocab;

    public SeedPatentVectors(int startDate) throws Exception {
        this.date=startDate;

        // Create Iterator
        SentenceIterator sentenceIterator = new BasePatentIterator(date);

        SentenceTransformer transformer = new SentenceTransformer.Builder()
                .readOnly(true)
                .tokenizerFactory(new DefaultTokenizerFactory())
                .iterator(sentenceIterator)
                .build();

        iterator = new AbstractSequenceIterator.Builder<>(transformer).build();

        // Start on Vocabulary
        if(!vocabFile.exists()) {
            System.out.println("Starting to build vocabulary...");
            buildAndWriteVocabulary();
        } else {
            System.out.println("Loading vocabulary...");
            vocab = WordVectorSerializer.readVocabCache(vocabFile);
        }

        // Start on WordVectors
        System.out.println("Starting Word Vectors...");
        if(!wordVectorsFile.exists()) buildAndWriteParagraphVectors();
        else {
            wordVectors = WordVectorSerializer.loadFullModel(wordVectorsFile.getAbsolutePath());
        }
        System.out.println("Finished loading Word Vector Model...");

        // Now write vectors to DB
        Database.setupMainConn();
        int timeToCommit = 0;
        final int commitLength = 1000;
        AvgWordVectorIterator vectorIterator = new AvgWordVectorIterator(wordVectors, date);
        try {
            while (vectorIterator.hasNext()) {
                timeToCommit++;
                PatentVectors patent = vectorIterator.next();
                System.out.println(patent.getPubDocNumber());
                printVector("Title", patent.getTitleWordVectors());
                try {
                    // make sure nothing is null
                    if (patent.isValid()) {
                        Database.insertPatentVectors(patent.getPubDocNumber(), patent.getPubDate(),
                                patent.getTitleWordVectors(), patent.getAbstractWordVectors(), patent.getDescriptionWordVectors());
                    }
                } catch (Exception e) {
                    System.out.print("WHILE CALCULATING PATENT: " + patent.getPubDocNumber());
                    e.printStackTrace();
                    if (e instanceof SQLException) throw new RuntimeException("Database Error!!"); // Termin
                }
                if (timeToCommit % commitLength == 0) Database.commit();
                timeToCommit = (timeToCommit + 1) % commitLength;
            }
        } finally {
            Database.commit();
        }
    }

    private void printVector(String name, Double[][] vector) {
        System.out.println(name+": "+Arrays.toString(vector));
    }

    private void buildAndWriteVocabulary() throws IOException {
        vocab = new AbstractCache.Builder<VocabWord>()
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

        WordVectorSerializer.writeVocabCache(vocab, vocabFile);
    }

    private void buildAndWriteParagraphVectors() throws IOException {
        WeightLookupTable<VocabWord> lookupTable = new InMemoryLookupTable.Builder<VocabWord>()
                .useAdaGrad(false)
                .vectorLength(Constants.VECTOR_LENGTH)
                .seed(41)
                .cache(vocab)
                .build();

        lookupTable.resetWeights(true);

        wordVectors = new Word2Vec.Builder()
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

        WordVectorSerializer.writeFullModel(wordVectors, wordVectorsFile.getAbsolutePath());
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            new SeedPatentVectors(20050000);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }

}
