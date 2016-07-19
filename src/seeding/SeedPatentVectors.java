package seeding;

import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import tools.WordVectorSerializer;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.Arrays;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedPatentVectors {
    private int date;
    private static final File paragraphVectorFile = new File(Constants.COMPDB_PARAGRAPH_VECTORS);
    private ParagraphVectors paragraphVectors;
    private TokenizerFactory tokenizerFactory;
    private LabelAwareSentenceIterator iterator;

    public SeedPatentVectors(int startDate) throws Exception {
        this.date=startDate;

        // Tokenizer Factor
        tokenizerFactory = new DefaultTokenizerFactory();
        //tokenizerFactory.setTokenPreProcessor(str->str);

        // Create Iterator
        iterator = new BasePatentIterator(date);

        // Start on ParagraphVectors
        System.out.println("Starting Paragraph Vectors...");
        if(!paragraphVectorFile.exists()) buildAndWriteParagraphVectors();
        else {
            paragraphVectors = WordVectorSerializer.readParagraphVectorsFromText(paragraphVectorFile);
            paragraphVectors.setTokenizerFactory(tokenizerFactory);
        }

        System.out.println("Finished loading Paragraph Vector Model...");
        // Now write vectors to DB
        Database.setupMainConn();
        int timeToCommit = 0;
        final int commitLength = 1000;
        ResultSet rs = Database.getPatentDataWithTitleAndDate(startDate);
        while(rs.next()) {
            timeToCommit++;
            String pub_doc_number = rs.getString(1);
            Integer pub_date = rs.getInt(2);
            try {
                Double[] invention_title = computeAvgWordVectorsFrom(rs.getString(3));
                printVector("Invention Title",invention_title);
                Double[] abstract_vectors = getParagraphVectorMatrixFrom(rs.getString(4));
                printVector("Abstract",abstract_vectors);
                Double[] description = getParagraphVectorMatrixFrom(rs.getString(5));
                printVector("Description",description);

                //Database.insertPatentVectors(pub_doc_number,pub_date,invention_title,abstract_vectors,description);
            } catch(Exception e) {
                System.out.print("WHILE CALCULATING PATENT: "+pub_doc_number);
                e.printStackTrace();
            }
            if(timeToCommit % commitLength == 0) Database.commit();
            timeToCommit = (timeToCommit+1) % commitLength;
        }
        Database.commit();
    }

    private void printVector(String name, Double[] vector) {
        System.out.println(name+": "+Arrays.toString(vector));
    }

    private Double[] getParagraphVectorMatrixFrom(String sentence) {
        return toObject(paragraphVectors.inferVector(sentence).data().asDouble());
    }

    private Double[] computeAvgWordVectorsFrom(String sentence) {
        INDArray wordVector = null;
        if(sentence!=null) {
            int size = 0;
            for (String word : sentence.split("\\s+")) {
                if (word == null || !paragraphVectors.hasWord(word)) continue;
                if(wordVector==null) wordVector = paragraphVectors.getWordVectorMatrix(word);
                else wordVector.add(paragraphVectors.getWordVectorMatrix(word));
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
        paragraphVectors = new ParagraphVectors.Builder()
                .seed(41)
                .useAdaGrad(false)
                .resetModel(true)
                .batchSize(1000)
                .trainWordVectors(true)
                .epochs(1)
                .iterations(5)
                .tokenizerFactory(tokenizerFactory)
                .windowSize(10)
                .iterate(iterator)
                .layerSize(Constants.VECTOR_LENGTH)
                .stopWords(Arrays.asList(Constants.STOP_WORDS))
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .learningRate(0.01)
                .minLearningRate(0.0001)
                .build();

        paragraphVectors.fit();

        WordVectorSerializer.writeWordVectors(paragraphVectors, paragraphVectorFile);
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
