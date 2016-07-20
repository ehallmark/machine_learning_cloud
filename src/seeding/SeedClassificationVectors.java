package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.nd4j.linalg.api.ndarray.INDArray;
import tools.VectorHelper;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedClassificationVectors {
    private List<String> classifications;
    private WordVectors wordVectors;
    private File wordVectorsFile = new File(Constants.WORD_VECTORS_PATH);

    public SeedClassificationVectors(int startDate) throws Exception {
        if(!wordVectorsFile.exists()) throw new RuntimeException("NO WORD VECTORS FILE FOUND!");
        wordVectors = WordVectorSerializer.loadFullModel(wordVectorsFile.getAbsolutePath());

        classifications = Database.getDistinctClassifications();
        System.out.println("Number of classifications: "+classifications.size());

        // Get pub_doc_numbers grouped by date
        ResultSet patentNumbers = Database.getPatentsAfter(startDate);
        while(patentNumbers.next()) {
            ResultSet rs = Database.getClassificationsFromPatents(patentNumbers.getArray(1));
            Integer pubDate = patentNumbers.getInt(2);
            while (rs.next()) {
                String pubDocNumber = rs.getString(1);

                // class_softmax
                Double[] classSoftMax = new Double[classifications.size()];
                Arrays.fill(classSoftMax, 0.0);
                String[] classes = (String[]) rs.getArray(2).getArray();
                if (classes != null) {
                    for (String klass : classes) {
                        classSoftMax[classifications.indexOf(klass)] = 1.0 / classes.length;
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

                System.out.println(pubDocNumber);
                // Update patent classification vector
                Database.insertClassifications(pubDocNumber, pubDate, classSoftMax, classVector, subClassVector);
            }
            Database.commit();

        }


    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            Database.setupMainConn();
            new SeedClassificationVectors(Constants.START_DATE);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.commit();
            Database.close();
        }
    }
}
