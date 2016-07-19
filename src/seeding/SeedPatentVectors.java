package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.nd4j.linalg.api.ndarray.INDArray;
import tools.WordVectorSerializer;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedPatentVectors {
    private int date;
    private static final File sequenceVectorsFile = new File(Constants.SEQUENCE_VECTORS_FILE);
    private SequenceVectors<VocabWord> sequenceVectors;
    private SequenceIterator<VocabWord> iterator;

    public SeedPatentVectors(int startDate) throws Exception {
        this.date=startDate;

        // Create Iterator
        iterator = new BasePatentIterator(date);

        // Start on ParagraphVectors
        System.out.println("Starting Paragraph Vectors...");
        if(!sequenceVectorsFile.exists()) buildAndWriteParagraphVectors();
        else {
            sequenceVectors = WordVectorSerializer.readSequenceVectors(new VocabWordFactory(), sequenceVectorsFile);
        }


        System.out.println("Finished loading Paragraph Vector Model...");
        // Now write vectors to DB
        Database.setupMainConn();
        int timeToCommit = 0;
        final int commitLength = 1000;
        ResultSet rs = Database.getPatentDataWithTitleAndDate(startDate);
        try {
            while (rs.next()) {
                timeToCommit++;
                String pub_doc_number = rs.getString(1);
                Integer pub_date = rs.getInt(2);
                try {
                    Double[] invention_title = computeAvgWordVectorsFrom(rs.getString(3));
                    printVector("Invention Title", invention_title);
                    // Use average word vectors for now
                    //Double[] abstract_vectors = getParagraphVectorMatrixFrom(rs.getString(4));
                    Double[] abstract_vectors = computeAvgWordVectorsFrom(rs.getString(4));
                    printVector("Abstract", abstract_vectors);
                    //Double[] description = getParagraphVectorMatrixFrom(rs.getString(5));
                    Double[] description = computeAvgWordVectorsFrom(rs.getString(5));
                    printVector("Description", description);

                    // make sure nothing is null
                    if (!(invention_title == null || abstract_vectors == null || description == null)) {
                        Database.insertPatentVectors(pub_doc_number, pub_date, invention_title, abstract_vectors, description);
                    }
                } catch (Exception e) {
                    System.out.print("WHILE CALCULATING PATENT: " + pub_doc_number);
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

    private void printVector(String name, Double[] vector) {
        System.out.println(name+": "+Arrays.toString(vector));
    }

    private Double[] computeAvgWordVectorsFrom(String sentence) {
        INDArray wordVector = null;
        if(sentence!=null) {
            int size = 0;
            for (String word : sentence.split("\\s+")) {
                if (word == null || !sequenceVectors.hasWord(word)) continue;
                if(wordVector==null) wordVector = sequenceVectors.getWordVectorMatrix(word);
                else wordVector.add(sequenceVectors.getWordVectorMatrix(word));
                size++;
            }
            if (size > 0) wordVector.div(size);
        }
        if(wordVector!=null) return toObject(wordVector.data().asDouble());
        else return null;
    }

    private Double[] toObject(double[] primArray) {
        Double[] vec = new Double[primArray.length];
        int i = 0;
        for(double d: primArray) {
            vec[i] = d;
            i++;
        }
        return vec;
    }

    private void buildAndWriteParagraphVectors() throws IOException {
        sequenceVectors = new SequenceVectors.Builder<VocabWord>()
                .seed(41)
                .useAdaGrad(false)
                .resetModel(true)
                .batchSize(1000)
                .trainElementsRepresentation(true)
                .trainSequencesRepresentation(false)
                .epochs(1)
                .iterations(4)
                .windowSize(7)
                .iterate(iterator)
                .layerSize(Constants.VECTOR_LENGTH)
                .stopWords(Arrays.asList(Constants.STOP_WORDS))
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .learningRate(0.01)
                .minLearningRate(0.0001)
                .build();

        sequenceVectors.fit();

        WordVectorSerializer.writeSequenceVectors(sequenceVectors, new VocabWordFactory(), sequenceVectorsFile);
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
