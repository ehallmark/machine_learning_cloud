package server;

import learning.LabelSeeker;
import learning.MeansBuilder;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.FileLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.StemmingPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import seeding.DatabaseIterator;
import seeding.MyPreprocessor;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;
import static spark.Spark.post;
import static spark.Spark.get;

/**
 * Created by ehallmark on 6/24/16.
 */
public class CompDB {
    private static ParagraphVectors paragraphVectors;
    private static MeansBuilder meansBuilder;
    private static LabelSeeker seeker;
    private static TokenizerFactory tokenizerFactory;
    private static DatabaseIterator iterator;

    public static void setupModel() throws Exception {
        // Check for paragraphVectors.obj file
        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());

        File pVectors = new File("paragraphVectors.txt");
        if(pVectors.exists()) {
            paragraphVectors = WordVectorSerializer.readParagraphVectorsFromText(pVectors);
        } else {
            throw new Exception("Cannot find paragraph vectors...");
        }

        // build a iterator for our dataset
        iterator = new DatabaseIterator(false);

        meansBuilder = new MeansBuilder(
                (InMemoryLookupTable<VocabWord>)paragraphVectors.getLookupTable(),
                tokenizerFactory);
        seeker = new LabelSeeker(iterator.getLabels(),
                (InMemoryLookupTable<VocabWord>) paragraphVectors.getLookupTable());

    }

    public static void server() {

        get("/", (req,res)->{
            return "Home";
        });

        get("/find_by_text", (req, res) -> {
            String patent = req.queryParams("patent");
            if(patent==null) return "Please enter a patent";
            LabelledDocument document = new LabelledDocument();
            document.setContent(iterator.getPatentAbstractWords(patent));
            document.setLabel(patent);
            System.out.println("Document label: "+patent);

            List<Pair<String, Double>> scores = null;
            try {
                INDArray documentAsCentroid = meansBuilder.documentAsVector(document);
                scores = seeker.getScores(documentAsCentroid);
            } catch (Exception e) {
                e.printStackTrace();
                return "Error calculating centroid.";
            }

            scores.sort(new Comparator<Pair<String,Double>>() {
                @Override
                public int compare(Pair<String, Double> o1, Pair<String, Double> o2) {
                    return (o2.getSecond().compareTo(o1.getSecond()));
                }

            });

            // update stats
            int numGuesses = 10;
            String[] guesses = new String[numGuesses];
            for(int i =0; i < numGuesses; i++) {
                guesses[i]=scores.get(i).getFirst();
            }

            return Arrays.toString(guesses);
        });

    }

    public static void main(String[] args) {
        try {
            // get the paragraph vector model
            setupModel();

            // Start server
            Database.setupMainConn();
            server();

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
