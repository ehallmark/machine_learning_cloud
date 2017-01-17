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
    public static File claimsParagraphVectorFile = new File("claims_skipgram.paragraphvectors");
    public static File allParagraphsModelFile = new File("all_paragraphs.paragraphvectors");
    public static File allSentencesModelFile = new File("all_sentences_skipgram.paragraphvectors");
    private static TokenizerFactory tokenizerFactory = new MyTokenizerFactory();
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    private ParagraphVectors net;
    private int minElementFrequency = 15;
    private int windowSize = 4;
    private int layerSize = 500;
    private double sampling = 0.00001;
    private double learningRate = 0.05;
    private double negativeSampling = -1;//30;



   /* public void trainAndSaveClaimModel() throws SQLException {
        SequenceIterator<VocabWord> sentenceIterator = DatabaseIteratorFactory.PatentClaimSequenceIterator();
        net = new ParagraphVectors.Builder()
                .seed(41)
                .batchSize(100)
                .epochs(100)
                .windowSize(windowSize)
                .layerSize(layerSize)
                .sampling(sampling)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(minElementFrequency)
                .workers(8)
                .iterations(3)
                .stopWords(new ArrayList<String>(Constants.CLAIM_STOP_WORD_SET))
                .trainWordVectors(true)
                .trainSequencesRepresentation(true)
                .trainElementsRepresentation(true)
                .elementsLearningAlgorithm(new SkipGram<>())
                .sequenceLearningAlgorithm(new DBOW<>())
                .tokenizerFactory(tokenizerFactory)
                .setVectorsListeners(Arrays.asList(
                        new CustomWordVectorListener(claimsParagraphVectorFile,"Claim Paragraph Vectors",1000000,null,"claim","device","femto","finance","touchscreen","smartphone","internet","semiconductor","artificial","intelligence")
                ))
                .iterate(sentenceIterator)
                .build();
        net.fit();
        WordVectorSerializer.writeWordVectors(net, claimsParagraphVectorFile.getAbsolutePath());
    }
    public void trainAndSaveDescriptionModel() throws SQLException {
        SequenceIterator<VocabWord> sentenceIterator = DatabaseIteratorFactory.PatentSequenceIterator();
        net = new ParagraphVectors.Builder()
                .seed(41)
                .batchSize(100)
                .epochs(100)
                .windowSize(windowSize)
                .layerSize(layerSize)
                .sampling(sampling)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(minElementFrequency)
                .workers(8)
                .iterations(1)
                .stopWords(new ArrayList<String>(Constants.STOP_WORD_SET))
                .trainWordVectors(true)
                .trainSequencesRepresentation(true)
                .trainElementsRepresentation(true)
                .elementsLearningAlgorithm(new SkipGram<>())
                .sequenceLearningAlgorithm(new DBOW<>())
                .tokenizerFactory(tokenizerFactory)
                .setVectorsListeners(Arrays.asList(
                        new CustomWordVectorListener(descriptionsParagraphVectorFile,"Description Paragraph Vectors",1000000,null,"claim","device","femto","finance","touchscreen","smartphone","internet","semiconductor","artificial","intelligence")
                ))
                .iterate(sentenceIterator)
                .build();
        net.fit();
        WordVectorSerializer.writeWordVectors(net, descriptionsParagraphVectorFile.getAbsolutePath());
    }
    public void trainAndSaveAllSentencesModel() throws SQLException {
        SequenceIterator<VocabWord> sentenceIterator = DatabaseIteratorFactory.PatentSentenceSequenceIterator();
        net = new ParagraphVectors.Builder()
                .seed(41)
                .batchSize(100)
                .epochs(1)
                .windowSize(4)
                .layerSize(300)
                .sampling(0.0001)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(150)
                .workers(6)
                .iterations(1)
                .stopWords(new ArrayList<String>(Constants.CLAIM_STOP_WORD_SET))
                .trainWordVectors(true)
                .trainSequencesRepresentation(true)
                .trainElementsRepresentation(true)
                .elementsLearningAlgorithm(new SkipGram<>())
                .sequenceLearningAlgorithm(new DBOW<>())
                .tokenizerFactory(tokenizerFactory)
                .setVectorsListeners(Arrays.asList(
                        new CustomWordVectorListener(allSentencesModelFile,"Paragraph Vectors All Sentences",10000000,null,"7455590","claim","alkali_metal","device","femto","finance","touchscreen","smartphone","internet","semiconductor","artificial","intelligence")
                ))
                .iterate(sentenceIterator)
                .build();
        net.fit();
        WordVectorSerializer.writeWordVectors(net, allSentencesModelFile.getAbsolutePath());
    }*/

    public void trainAndSaveParagraphVectorModel() throws SQLException {
        //CudaEnvironment.getInstance().getConfiguration().allowMultiGPU(true);
        int numEpochs = 3;
        int numThreads = 12;

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
                        new CustomWordVectorListener(allParagraphsModelFile,"Paragraph Vectors All Paragraphs",50000000,null,"7455590","claim","alkali_metal","device","femto","finance","touchscreen","smartphone","internet","semiconductor","artificial","intelligence")
                ))
                .iterate(sentenceIterator)
                .build();

        net.fit();
        WordVectorSerializer.writeParagraphVectors(net, allParagraphsModelFile.getAbsolutePath());
    }

    public static ParagraphVectors loadAllSentencesModel() throws IOException {
        // Write word vectors to file
        return WordVectorSerializer.readParagraphVectorsFromText(allSentencesModelFile.getAbsolutePath());
    }

    public static ParagraphVectors loadAllClaimsModel() throws IOException {
        // Write word vectors to file
        return WordVectorSerializer.readParagraphVectorsFromText(claimsParagraphVectorFile.getAbsolutePath());
    }


    public static ParagraphVectors loadParagraphsModel() throws IOException {
        return loadModel(allParagraphsModelFile.getAbsolutePath()+250000000);
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