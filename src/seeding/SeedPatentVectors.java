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
    private WordVectors wordVectors;
    private int timeToCommit;
    private long startTime;
    private final int commitLength;
    private AtomicInteger count;

    public SeedPatentVectors(int startDate, boolean useGoogleModel) throws Exception {
        this.date=startDate;

        if(!useGoogleModel) wordVectors = WordVectorSerializer.loadFullModel(wordVectorsFile.getAbsolutePath());
        else wordVectors = WordVectorSerializer.loadGoogleModel(googleVectorsFile, true, false);

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
