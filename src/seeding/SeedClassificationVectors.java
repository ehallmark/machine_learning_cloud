package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import tools.VectorHelper;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedClassificationVectors {
    private List<String> classifications;
    private WordVectors wordVectors;
    private static final File wordVectorsFile = new File(Constants.WORD_VECTORS_PATH);
    private static final File googleVectorsFile = new File(Constants.GOOGLE_WORD_VECTORS_PATH);
    public SeedClassificationVectors(int startDate, boolean useGoogleModel) throws Exception {
        if((!wordVectorsFile.exists() && !useGoogleModel) || (!googleVectorsFile.exists() && useGoogleModel)) throw new RuntimeException("Inconsistent Word Vector File Option");

        if(useGoogleModel) wordVectors = WordVectorSerializer.loadGoogleModel(googleVectorsFile, true, false);
        else wordVectors = WordVectorSerializer.loadFullModel(wordVectorsFile.getAbsolutePath());

        classifications = Database.getDistinctClassifications();
        System.out.println("Number of classifications: "+classifications.size());

        // Get pub_doc_numbers grouped by date
        ResultSet patentNumbers = Database.getPatentsAfter(startDate);
        int timeToCommit = 0;
        final int commitLength = 1000;
        long startTime = System.currentTimeMillis();
        AtomicInteger count = new AtomicInteger(0);
        while(patentNumbers.next()) {
            ResultSet rs = Database.getClassificationsFromPatents(patentNumbers.getArray(1));
            Integer pubDate = patentNumbers.getInt(2);
            if(pubDate==null) continue;
            while (rs.next()) {
                String pubDocNumber = rs.getString(1);
                if(pubDocNumber==null)continue;

                // class_softmax
                Double[] classSoftMax = new Double[classifications.size()];
                Arrays.fill(classSoftMax, 0.0);
                String[] classes = (String[]) rs.getArray(2).getArray();
                if (classes != null) {
                    for (String klass : classes) {
                        int index = classifications.indexOf(klass);
                        if(index>=0)classSoftMax[index] = 1.0 / classes.length;
                    }
                }

                // class vectors
                Double[] classVector = null;
                String sentence = rs.getString(3);
                if(sentence!=null) {
                    classVector = VectorHelper.computeAvgWordVectorsFrom(wordVectors, sentence);
                }

                // subclass vectors
                Double[] subClassVector = null;
                sentence = rs.getString(4);
                if(sentence!=null) {
                    subClassVector = VectorHelper.computeAvgWordVectorsFrom(wordVectors, sentence);
                }

                // Update patent classification vector
                if(classSoftMax!=null || classVector!=null || subClassVector !=null) {
                    Database.insertClassifications(pubDocNumber, pubDate, classSoftMax, classVector, subClassVector);
                    if (timeToCommit % commitLength == 0) {
                        Database.commit();
                        long endTime = System.currentTimeMillis();
                        System.out.println("Seconds to complete 1000 patents: "+new Double(endTime-startTime)/1000);
                        startTime = endTime;
                    }
                    timeToCommit = (timeToCommit + 1) % commitLength;
                    System.out.println(count.getAndIncrement());

                } else {
                    System.out.println("-");
                }


            }
            Database.commit();
        }
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            boolean useGoogle = true;
            new SeedClassificationVectors(Constants.START_DATE, useGoogle);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }
}
