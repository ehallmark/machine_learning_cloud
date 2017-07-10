package dl4j_neural_nets.vectorization;

import dl4j_neural_nets.iterators.sequences.AsyncSequenceIterator;
import dl4j_neural_nets.iterators.sequences.DatabaseIteratorFactory;
import dl4j_neural_nets.listeners.CustomWordVectorListener;
import dl4j_neural_nets.tools.MyPreprocessor;
import dl4j_neural_nets.tools.MyTokenizerFactory;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.learning.impl.sequence.DBOW;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import seeding.Constants;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by ehallmark on 11/30/16.
 */
public class ParagraphVectorModel {
    public static File allParagraphsModelFile = new File(Constants.DATA_FOLDER+"all_paragraphs2017.paragraphvectors");
    public static File testParagraphsModelFile = new File(Constants.DATA_FOLDER+"simple.pv");
    private static TokenizerFactory tokenizerFactory = new MyTokenizerFactory();
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    private ParagraphVectors net;
    private double learningRate = 0.05;
    private double negativeSampling = -1;//30;


    public static void runTestModel() throws Exception {
        int numThreads = 30;

        SequenceIterator<VocabWord> sentenceIterator = new AsyncSequenceIterator(DatabaseIteratorFactory.PatentParagraphSamplingSequenceIterator(1,1000),numThreads/2);

        ParagraphVectors net = new ParagraphVectors.Builder()
                .seed(41)
                .batchSize(100)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(6)
                .layerSize(50)
                .sampling(0.00005)
                .negativeSample(-1)
                .learningRate(0.01)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(30)
                .workers(numThreads/2)
                .iterations(1)
                .stopWords(new ArrayList<String>(Constants.CLAIM_STOP_WORD_SET))
                .trainWordVectors(true)
                .useHierarchicSoftmax(true)
                .trainSequencesRepresentation(true)
                .trainElementsRepresentation(true)
                .elementsLearningAlgorithm(new SkipGram<>())
                .sequenceLearningAlgorithm(new DBOW<>())
                .tokenizerFactory(tokenizerFactory)
                .iterate(sentenceIterator)
                .build();

        net.fit();
        WordVectorSerializer.writeParagraphVectors(net, testParagraphsModelFile.getAbsolutePath());
        net = WordVectorSerializer.readParagraphVectors(testParagraphsModelFile.getAbsolutePath());
        System.out.println("Sample vector: "+net.getLookupTable().vectors().next());
    }
    public void trainAndSaveParagraphVectorModel() throws SQLException {
        int numEpochs = 3;
        int numThreads = 60;

        SequenceIterator<VocabWord> sentenceIterator = new AsyncSequenceIterator(DatabaseIteratorFactory.PatentParagraphSequenceIterator(numEpochs),numThreads/2);

        net = new ParagraphVectors.Builder()
                .seed(41)
                .batchSize(1000)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(6)
                .layerSize(300)
                .sampling(0.00005)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(30)
                .workers(numThreads/2)
                .iterations(1)
                .stopWords(new ArrayList<String>(Constants.CLAIM_STOP_WORD_SET))
                .trainWordVectors(true)
                .useHierarchicSoftmax(true)
                .trainSequencesRepresentation(true)
                .trainElementsRepresentation(true)
                .elementsLearningAlgorithm(new SkipGram<>())
                .sequenceLearningAlgorithm(new DBOW<>())
                .tokenizerFactory(tokenizerFactory)
                .setVectorsListeners(Arrays.asList(
                        new CustomWordVectorListener(allParagraphsModelFile,"Paragraph Vectors All Paragraphs",100000000,null,"7455590","claim","alkali_metal","device","femto","finance","touchscreen","smartphone","internet","semiconductor","artificial","intelligence")
                ))
                .iterate(sentenceIterator)
                .build();

        net.fit();
        WordVectorSerializer.writeParagraphVectors(net, allParagraphsModelFile.getAbsolutePath());
    }

    public static ParagraphVectors loadParagraphsModel() throws IOException {
        return loadModel(allParagraphsModelFile.getAbsolutePath()+1000000000);
    }

    public static ParagraphVectors loadTestParagraphsModel() throws IOException {
        System.out.println("LOADING TEST MODEL!!! WARNING!!!!");
        return loadModel(testParagraphsModelFile.getAbsolutePath());
    }

    public static ParagraphVectors loadModel(String path) throws IOException {
        // Write word vectors to file
        ParagraphVectors vectors = WordVectorSerializer.readParagraphVectors(path);
        TokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new CommonPreprocessor());
        vectors.setTokenizerFactory(tf);
        vectors.getConfiguration().setIterations(30);
        return vectors;
    }

    public static void main(String[] args) {
        try {
            ParagraphVectorModel model = new ParagraphVectorModel();
            model.trainAndSaveParagraphVectorModel();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}