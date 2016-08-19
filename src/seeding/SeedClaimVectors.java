package seeding;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import tools.VectorHelper;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedClaimVectors {
    private ParagraphVectors wordVectors;
    private static final File wordVectorsFile = new File(Constants.WORD_VECTORS_PATH);
    private AtomicInteger count;
    private final int commitLength;
    private int timeToCommit;
    private long startTime;
    public SeedClaimVectors(int startDate, boolean updateDates) throws Exception {
        if(!wordVectorsFile.exists()) throw new RuntimeException("Inconsistent Word Vector File Option");
        wordVectors = WordVectorSerializer.readParagraphVectorsFromText(wordVectorsFile.getAbsolutePath());

        count = new AtomicInteger(0);
        timeToCommit = 0;
        commitLength = 1000;
        startTime = System.currentTimeMillis();

        // Get compdbPatents
        ResultSet compdbPatentNumbers = Database.compdbPatentsGroupedByDate();
        getPubDateAndPatentNumbersFromResultSet(compdbPatentNumbers,false);

        // Get pub_doc_numbers grouped by date
        ResultSet patentNumbers = Database.getPatentsBetween(startDate);
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
            String[] claims = (String[])rs.getArray(2).getArray();
            Integer[] claimNumbers = (Integer[])rs.getArray(3).getArray();
            List<Double[]> claimVector = null;
            List<Integer> numbers = null;
            if(claims!=null && claimNumbers!=null) {
                claimVector = new ArrayList<>(claims.length);
                numbers = new ArrayList<>(claims.length);
                AtomicInteger i = new AtomicInteger(0);
                for(String claim : claims) {
                    Double[] data = VectorHelper.computeAvgWordVectorsFrom(wordVectors, claim);
                    Integer num = claimNumbers[i.getAndIncrement()];
                    if(data==null || num==null) continue;
                    claimVector.add(data);
                    numbers.add(num);
                }
            }

            // Update patent claim vector
            if(claimVector !=null && !claimVector.isEmpty()) {
                Database.insertClaims(pubDocNumber, pubDate, claimVector.toArray(new Double[claimVector.size()][]), numbers.toArray(new Integer[numbers.size()]));
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
            boolean useGoogle = false;
            // Get Last Date
            int startDate = Database.selectLastDate(Constants.CLAIM_VECTOR_TYPE);
            //int startDate = Constants.START_DATE;
            boolean updateDates = true;
            new SeedClaimVectors(startDate, updateDates);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }
}
