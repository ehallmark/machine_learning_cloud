package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
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
import tools.VectorHelper;
import tools.WordVectorSerializer;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedPatentVectors {
    private int date;
    private static final File wordVectorsFile = new File(Constants.WORD_VECTORS_PATH);
    private static final File googleVectorsFile = new File(Constants.GOOGLE_WORD_VECTORS_PATH);
    private static final File vocabFile = new File(Constants.VOCAB_FILE);
    private WordVectors wordVectors;
    private SequenceIterator<VocabWord> iterator;
    private VocabCache<VocabWord> vocab;
    private int timeToCommit;
    private long startTime;
    private final int commitLength;
    private AtomicInteger count;

    public SeedPatentVectors(int startDate, boolean useGoogleModel) throws Exception {
        this.date=startDate;

        if(!useGoogleModel) {
            // Create Iterator
            SentenceIterator sentenceIterator = new BasePatentIterator(date);

            SentenceTransformer transformer = new SentenceTransformer.Builder()
                    .readOnly(true)
                    .tokenizerFactory(new DefaultTokenizerFactory())
                    .iterator(sentenceIterator)
                    .build();

            iterator = new AbstractSequenceIterator.Builder<>(transformer).build();

            // Start on Vocabulary
            if (!vocabFile.exists()) {
                System.out.println("Starting to build vocabulary...");
                buildAndWriteVocabulary();
            } else {
                System.out.println("Loading vocabulary...");
                vocab = WordVectorSerializer.readVocabCache(vocabFile);
            }

            // Start on WordVectors
            System.out.println("Starting Word Vectors...");
            if (!wordVectorsFile.exists()) buildAndWriteParagraphVectors();
            else {
                wordVectors = WordVectorSerializer.loadFullModel(wordVectorsFile.getAbsolutePath());
            }
        } else {
            wordVectors = WordVectorSerializer.loadGoogleModel(googleVectorsFile, true, false);
        }
        System.out.println("Finished loading Word Vector Model...");

        // Now write vectors to DB
        timeToCommit = 0;
        commitLength = 1000;
        startTime = System.currentTimeMillis();
        count = new AtomicInteger(0);

        // Get compdbPatents
        //ResultSet compdbPatentNumbers = Database.compdbPatentsGroupedByDate();
        //getPubDateAndPatentNumbersFromResultSet(compdbPatentNumbers,false);

        // Get pub_doc_numbers grouped by date
        ResultSet patentNumbers = Database.getPatentsAfter(startDate);
        getPubDateAndPatentNumbersFromResultSet(patentNumbers,true);
    }

    private void getPubDateAndPatentNumbersFromResultSet(ResultSet patentNumbers, boolean updateDate) throws SQLException, InterruptedException, ExecutionException {
        while(patentNumbers.next()) {
            Integer pubDate = patentNumbers.getInt(2);
            if(pubDate==null) continue;
            ResultSet rs = Database.getMainVectorsFromPatentArray(patentNumbers.getArray(1));
            handleResultSetGroupedByDate(rs);
            // update date
            if(updateDate)Database.updateLastDate(Constants.PATENT_VECTOR_TYPE,pubDate);
            Database.commit();
        }
    }

    private void handleResultSetGroupedByDate(ResultSet rs) throws SQLException, InterruptedException, ExecutionException{
        while(rs.next()) {
            handlePatentVectorObject(VectorHelper.getPatentVectors(rs,wordVectors));
        }
    }

    private void handlePatentVectorObject(PatentVectors patent) throws SQLException {
        if (patent.isValid()) {
            Database.insertPatentVectors(patent.getPubDocNumber(), patent.getPubDate(),
                    patent.getTitleWordVectors(), patent.getAbstractWordVectors(), patent.getDescriptionWordVectors());

            if (timeToCommit % commitLength == 0) {
                Database.commit();
                long endTime = System.currentTimeMillis();
                System.out.println("Seconds to complete 1000 patents: " + new Double(endTime - startTime) / 1000);
                startTime = endTime;
            }
            timeToCommit = (timeToCommit + 1) % commitLength;
            System.out.println(count.getAndIncrement());
        } else {
            System.out.println("-");
        }
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

        ((Word2Vec)wordVectors).fit();

        WordVectorSerializer.writeFullModel((Word2Vec)wordVectors, wordVectorsFile.getAbsolutePath());
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            Database.setupCompDBConn();
            boolean useGoogle = true;
            int startDate = Database.selectLastDate(Constants.PATENT_VECTOR_TYPE);
            new SeedPatentVectors(startDate,useGoogle);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }

}
