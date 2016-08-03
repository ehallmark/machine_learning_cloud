package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import tools.VectorHelper;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedClaimVectors {
    private WordVectors wordVectors;
    private static final File wordVectorsFile = new File(Constants.WORD_VECTORS_PATH);
    private static final File googleVectorsFile = new File(Constants.GOOGLE_WORD_VECTORS_PATH);
    private AtomicInteger count;
    private final int commitLength;
    private int timeToCommit;
    private long startTime;
    public SeedClaimVectors(int startDate, int endDate, boolean useGoogleModel, boolean updateDates) throws Exception {
        assert startDate < endDate: "Start date must be before end date!";
        if((!wordVectorsFile.exists() && !useGoogleModel) || (!googleVectorsFile.exists() && useGoogleModel)) throw new RuntimeException("Inconsistent Word Vector File Option");

        if(useGoogleModel) wordVectors = WordVectorSerializer.loadGoogleModel(googleVectorsFile, true, false);
        else wordVectors = WordVectorSerializer.loadFullModel(wordVectorsFile.getAbsolutePath());

        count = new AtomicInteger(0);
        timeToCommit = 0;
        commitLength = 1000;
        startTime = System.currentTimeMillis();

        // Get compdbPatents
        //ResultSet compdbPatentNumbers = Database.compdbPatentsGroupedByDate();
        //getPubDateAndPatentNumbersFromResultSet(compdbPatentNumbers,false);

        // Get pub_doc_numbers grouped by date
        ResultSet patentNumbers = Database.getPatentsBetween(startDate, endDate);
        getPubDateAndPatentNumbersFromResultSet(patentNumbers,updateDates);

    }

    private void getPubDateAndPatentNumbersFromResultSet(ResultSet patentNumbers, boolean updateDate) throws SQLException {
        while(patentNumbers.next()) {
            Integer pubDate = patentNumbers.getInt(2);
            if(pubDate==null) continue;
            ResultSet rs = Database.getClaimsFromPatents(patentNumbers.getArray(1));
            handleResultSetGroupedByDate(rs, pubDate);
            // update date
            if(updateDate)Database.updateLastDate(Constants.CLAIM_VECTOR_TYPE,pubDate);
            Database.commit();
        }
    }

    private void handleResultSetGroupedByDate(ResultSet rs, int pubDate) throws SQLException {
        while (rs.next()) {
            String pubDocNumber = rs.getString(1);
            if(pubDocNumber==null)continue;

            // Claim vectors
            Double[][] claimVector = null;
            String[] claims = (String[])rs.getArray(2).getArray();
            Integer[] claimNumbers = (Integer[])rs.getArray(3).getArray();
            if(claims!=null && claimNumbers!=null) {
                claimVector = VectorHelper.compute2DAvgWordVectorsFrom(wordVectors, claims);
            }

            // Update patent claim vector
            if(claimVector !=null) {
                Database.insertClaims(pubDocNumber, pubDate, claimVector, claimNumbers);
                if (timeToCommit % commitLength == 0) {
                    Database.commit();
                    long endTime = System.currentTimeMillis();
                    System.out.println("Seconds to complete 1000 patents: "+new Double(endTime-startTime)/1000);
                    startTime = endTime;
                }
                timeToCommit = (timeToCommit + 1) % commitLength;
            }

            System.out.println(count.getAndIncrement());
        }
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            Database.setupCompDBConn();
            GenerateVocabulary genVocab = new GenerateVocabulary(new PatentClaimIterator(Constants.VOCAB_START_DATE));
            VectorHelper.setupVocab(genVocab.getCache());
            boolean useGoogle = true;
            // Get Last Date
            //int startDate = Database.selectLastDate(Constants.CLAIM_VECTOR_TYPE);
            int startDate = Constants.START_DATE;
            int endDate = 20050001;
            boolean updateDates = false;
            new SeedClaimVectors(startDate, endDate, useGoogle, updateDates);

        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }
}
