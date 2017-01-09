package dl4j_neural_nets.vectorization;

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
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.buffer.util.DataTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public static File descriptionsParagraphVectorFile = new File("descriptions_skipgram.paragraphvectors");
    public static File allSentencesModelFile = new File("all_sentences_skipgram.paragraphvectors");
    private static TokenizerFactory tokenizerFactory = new MyTokenizerFactory();
    private static Logger log = LoggerFactory.getLogger(ParagraphVectorModel.class);
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
        int numEpochs = 5;
        SequenceIterator<VocabWord> sentenceIterator = DatabaseIteratorFactory.PatentParagraphSequenceIterator(numEpochs);
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
                .minWordFrequency(350)
                .workers(10)
                .iterations(3)
                .stopWords(new ArrayList<String>(Constants.CLAIM_STOP_WORD_SET))
                .trainWordVectors(true)
                .trainSequencesRepresentation(true)
                .trainElementsRepresentation(true)
                .elementsLearningAlgorithm(new SkipGram<>())
                .sequenceLearningAlgorithm(new DBOW<>())
                .tokenizerFactory(tokenizerFactory)
                .setVectorsListeners(Arrays.asList(
                        new CustomWordVectorListener(allParagraphsModelFile,"Paragraph Vectors All Paragraphs",5000000,null,"7455590","claim","alkali_metal","device","femto","finance","touchscreen","smartphone","internet","semiconductor","artificial","intelligence")
                ))
                .iterate(sentenceIterator)
                .build();
        net.fit();
        WordVectorSerializer.writeWordVectors(net, allParagraphsModelFile.getAbsolutePath());
    }

    public static ParagraphVectors loadClaimModel() throws IOException {
        // Write word vectors to file
        return WordVectorSerializer.readParagraphVectorsFromText(claimsParagraphVectorFile.getAbsolutePath()+"10");
    }

    public static ParagraphVectors loadDescriptionModel() throws IOException {
        // Write word vectors to file
        return WordVectorSerializer.readParagraphVectorsFromText(descriptionsParagraphVectorFile.getAbsolutePath());
    }

    public static ParagraphVectors loadAllSentencesModel() throws IOException {
        // Write word vectors to file
        return WordVectorSerializer.readParagraphVectorsFromText(allSentencesModelFile.getAbsolutePath());
    }

    public static ParagraphVectors loadParagraphsModel() throws IOException {
        return WordVectorSerializer.readParagraphVectorsFromText(allParagraphsModelFile.getAbsolutePath());
    }

    public static ParagraphVectors loadModel(String path) throws IOException {
        // Write word vectors to file
        return WordVectorSerializer.readParagraphVectorsFromText(path);
    }

    public static void main(String[] args) {
        try {
            DataTypeUtil.setDTypeForContext(DataBuffer.Type.FLOAT);
            ParagraphVectorModel model = new ParagraphVectorModel();
            model.trainAndSaveParagraphVectorModel();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
