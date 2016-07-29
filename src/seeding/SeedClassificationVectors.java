package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import tools.VectorBuilderThread;
import tools.VectorHelper;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedClassificationVectors {
    private List<String> classifications;
    private WordVectors wordVectors;
    private static final File wordVectorsFile = new File(Constants.WORD_VECTORS_PATH);
    private static final File googleVectorsFile = new File(Constants.GOOGLE_WORD_VECTORS_PATH);
    private final int commitLength;
    private int timeToCommit;
    private long startTime;
    private AtomicInteger count;

    public SeedClassificationVectors(int startDate, boolean useGoogleModel) throws Exception {
        if((!wordVectorsFile.exists() && !useGoogleModel) || (!googleVectorsFile.exists() && useGoogleModel)) throw new RuntimeException("Inconsistent Word Vector File Option");

        if(useGoogleModel) wordVectors = WordVectorSerializer.loadGoogleModel(googleVectorsFile, true, false);
        else wordVectors = WordVectorSerializer.loadFullModel(wordVectorsFile.getAbsolutePath());

        classifications = Database.getDistinctClassifications();
        System.out.println("Number of classifications: "+classifications.size());

        timeToCommit = 0;
        commitLength = 1000;
        startTime = System.currentTimeMillis();
        count = new AtomicInteger(0);

        // Get compdb patents grouped by date
        //ResultSet compdbPatentNumbers = Database.compdbPatentsGroupedByDate();
        //getPubDateAndPatentNumbersFromResultSet(compdbPatentNumbers,false);

        // Get pub_doc_numbers grouped by date
        ResultSet patentNumbers = Database.getPatentsAfter(startDate);
        getPubDateAndPatentNumbersFromResultSet(patentNumbers,true);

    }

    private void getPubDateAndPatentNumbersFromResultSet(ResultSet patentNumbers, boolean updateDate) throws SQLException {
        while(patentNumbers.next()) {
            ResultSet rs = Database.getClassificationsFromPatents(patentNumbers.getArray(1));
            Integer pubDate = patentNumbers.getInt(2);
            if(pubDate==null) continue;
            handleResultSetGroupedByDate(rs, pubDate);
            if(updateDate)Database.updateLastDate(Constants.CLASSIFICATION_VECTOR_TYPE,pubDate);
            Database.commit();
        }
    }

    private void handleResultSetGroupedByDate(ResultSet rs, int pubDate) throws SQLException {
        while (rs.next()) {
            String pubDocNumber = rs.getString(1);
            if(pubDocNumber==null)continue;

            RecursiveTask<Double[]> softmaxTask = new RecursiveTask<Double[]>() {
                @Override
                protected Double[] compute() {
                    // class_softmax
                    Double[] classSoftMax = new Double[classifications.size()];
                    Arrays.fill(classSoftMax, 0.0);
                    try {
                        String[] classes = (String[]) rs.getArray(2).getArray();
                        if (classes != null) {
                            for (String klass : classes) {
                                int index = classifications.indexOf(klass);
                                if(index>=0)classSoftMax[index] = 1.0/classes.length;
                            }
                        }
                        return classSoftMax;
                    } catch(SQLException sql) {
                        sql.printStackTrace();
                        return null;
                    }

                }
            };
            softmaxTask.fork();


            // class vectors
            VectorBuilderThread classThread = null;
            String sentence = rs.getString(3);
            if(sentence!=null) {
                classThread = new VectorBuilderThread(wordVectors, sentence);
                classThread.fork();
            }

            // subclass vectors
            VectorBuilderThread subClassThread = null;
            sentence = rs.getString(4);
            if(sentence!=null) {
                subClassThread = new VectorBuilderThread(wordVectors, sentence);
                subClassThread.fork();
            }

            // Update patent classification vector
            Double[] classSoftMax = null;
            Double[] classVector = null;
            Double[] subClassVector = null;
            try {
                classSoftMax = softmaxTask.get();
                if(classThread!=null) classVector = classThread.get();
                if(subClassThread!=null) subClassVector = subClassThread.get();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            } catch (ExecutionException ee) {
                ee.printStackTrace();
            }

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
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            Database.setupCompDBConn();
            VectorHelper.setupVocab(new File(Constants.VOCAB_FILE));

            boolean useGoogle = true;
            int startDate = Database.selectLastDate(Constants.CLASSIFICATION_VECTOR_TYPE);
            new SeedClassificationVectors(startDate, useGoogle);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }
}
