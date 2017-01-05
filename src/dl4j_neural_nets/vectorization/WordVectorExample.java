package dl4j_neural_nets.vectorization;

import dl4j_neural_nets.listeners.CustomWordVectorListener;
import dl4j_neural_nets.tools.MyTokenizerFactory;
import edu.stanford.nlp.util.Triple;
import org.deeplearning4j.models.embeddings.learning.impl.elements.CBOW;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareFileSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

/**
 * Created by agibsonccc on 10/9/14.
 *
 * Neural net that processes text into wordvectors. See below url for an in-depth explanation.
 * https://deeplearning4j.org/word2vec.html
 */
public class WordVectorExample {

    private static Logger log = LoggerFactory.getLogger(WordVectorExample.class);

    public static void main(String[] args) throws Exception {

        File trainFile = new File("/Users/ehallmark/Downloads/patentmachinelearning/training.1600000.processed.noemoticon.csv");
        File testFile = new File("/Users/ehallmark/Downloads/patentmachinelearning/testdata.manual.2009.06.14.csv");
        LabelAwareFileSentenceIterator trainIterator = new LabelAwareFileSentenceIterator(new File("paravec/labeled"));
        //new CSVTextIterator(trainFile,5,0);

        // Split on white spaces in the line to get words
        TokenizerFactory t = new MyTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());

        int vectorLength = 200;

        int listenerLines = 300;

        log.info("Building model....");
        ParagraphVectors vec = new ParagraphVectors.Builder()
                .minWordFrequency(1)
                .epochs(3)
                .iterations(5)
                .layerSize(vectorLength)
                .seed(41)
                .windowSize(10)
                .learningRate(0.1)
                .minLearningRate(0.0001)
                .elementsLearningAlgorithm(new CBOW<>())
                .useAdaGrad(true)
                .trainElementsRepresentation(true)
                .trainWordVectors(true)
                .trainSequencesRepresentation(true)
                .resetModel(true)
                .iterate(trainIterator)
                .setVectorsListeners(Arrays.asList(
                        new CustomWordVectorListener(null,"Example Model",listenerLines,Arrays.asList(
                                new Triple<>("mom","dad","brother"),
                                new Triple<>("night","evening","day"),
                                new Triple<>("inventory","health","bank")
                        ),"day","family","government","make_money")
                ))
                .sampling(0.5)
                //.negativeSample(30)
                .tokenizerFactory(t)
                .build();

        log.info("Fitting Word2Vec model....");
        vec.fit();

        log.info("Writing word vectors to text file....");

        // Write word vectors to file
        WordVectorSerializer.writeWordVectors(vec, "wordvectorexample2.txt");
        vec = WordVectorSerializer.readParagraphVectorsFromText("wordvectorexample2.txt");
        TokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new CommonPreprocessor());
        vec.setTokenizerFactory(tf);
        // Prints out the closest 10 words to "day". An example on what to do with these Word Vectors.
        log.info("Closest Words:");
        Collection<String> lst = vec.wordsNearest("day", 10);
        System.out.println("10 Words closest to 'day': " + lst);

        // Prints out the closest 10 words to "day". An example on what to do with these Word Vectors.
        log.info("Closest Words:");
        lst = vec.wordsNearest("inventory", 10);
        System.out.println("10 Words closest to 'inventory': " + lst);


        double simHealth = vec.similarityToLabel("medical","health");
        double simFinance = vec.similarityToLabel("medical","finance");

        System.out.println("Similarity to health: "+simHealth);
        System.out.println("Similarity to finance: "+simFinance);
    }
}