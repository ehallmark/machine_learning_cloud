package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

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
    private static final File wordVectorsFile = new File(Constants.WORD_VECTORS_PATH);
    private static final File googleVectorsFile = new File(Constants.GOOGLE_WORD_VECTORS_PATH);
    private WordVectors wordVectors;
    private int timeToCommit;
    private long startTime;
    private final int commitLength;
    private AtomicInteger count;

    public SeedPatentVectors(int startDate, int endDate, boolean useGoogleModel, boolean updateDates) throws Exception {
        assert startDate < endDate: "Start date must be before end date!";
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
        ResultSet patentNumbers = Database.getPatentsBetween(startDate,endDate);
        getPubDateAndPatentNumbersFromResultSet(patentNumbers,updateDates);
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
            GenerateVocabulary genVocab = new GenerateVocabulary(new BasePatentIterator(Constants.VOCAB_START_DATE));
            VectorHelper.setupVocab(genVocab.getCache());
            boolean useGoogle = true;
            //int startDate = Database.selectLastDate(Constants.PATENT_VECTOR_TYPE);
            int startDate = Constants.START_DATE;
            int endDate = 20050001;
            boolean updateDates = false;
            new SeedPatentVectors(startDate,endDate, useGoogle, updateDates);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }

}
