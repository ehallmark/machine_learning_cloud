package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import tools.VectorHelper;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedClaimVectors {
    private WordVectors wordVectors;
    private static final File wordVectorsFile = new File(Constants.WORD_VECTORS_PATH);
    private static final File googleVectorsFile = new File(Constants.GOOGLE_WORD_VECTORS_PATH);
    public SeedClaimVectors(int startDate, boolean useGoogleModel) throws Exception {
        if((!wordVectorsFile.exists() && !useGoogleModel) || (!googleVectorsFile.exists() && useGoogleModel)) throw new RuntimeException("Inconsistent Word Vector File Option");

        if(useGoogleModel) wordVectors = WordVectorSerializer.loadGoogleModel(googleVectorsFile, true, false);
        else wordVectors = WordVectorSerializer.loadFullModel(wordVectorsFile.getAbsolutePath());

        AtomicInteger count = new AtomicInteger(0);
        // Get pub_doc_numbers grouped by date
        ResultSet patentNumbers = Database.getPatentsAfter(startDate);
        int timeToCommit = 0;
        final int commitLength = 1000;
        long startTime = System.currentTimeMillis();
        while(patentNumbers.next()) {
            ResultSet rs = Database.getClaimsFromPatents(patentNumbers.getArray(1));
            Integer pubDate = patentNumbers.getInt(2);
            if(pubDate==null) continue;
            while (rs.next()) {
                String pubDocNumber = rs.getString(1);
                if(pubDocNumber==null)continue;

                // Claim vectors
                Double[][] claimVector = null;
                String[] claims = (String[])rs.getArray(2).getArray();
                if(claims!=null) {
                    claimVector = VectorHelper.createAndMerge2DWordVectors(wordVectors, claims);
                }

                // Update patent claim vector
                if(claimVector !=null) {
                    Database.insertClaims(pubDocNumber, pubDate, claimVector);
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
            Database.commit();
        }
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            boolean useGoogle = true;
            new SeedClaimVectors(Constants.START_DATE, useGoogle);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }
}
