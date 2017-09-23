package models.dl4j_neural_nets.vectorization;

import models.dl4j_neural_nets.iterators.sequences.AsyncSequenceIterator;
import models.dl4j_neural_nets.iterators.sequences.DatabaseIteratorFactory;
import models.dl4j_neural_nets.listeners.CustomWordVectorListener;
import models.dl4j_neural_nets.tools.MyPreprocessor;
import models.dl4j_neural_nets.tools.MyTokenizerFactory;
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
    public static File allParagraphsModelFile = new File(Constants.DATA_FOLDER+"titles_and_abstracts_2017-9-21.paragraphvectors");
    public static final int VECTOR_SIZE = 30;
    private static TokenizerFactory tokenizerFactory = new MyTokenizerFactory();
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    private ParagraphVectors net;
    private double learningRate = 0.025;
    private double negativeSampling = -1;//30;

    public void trainAndSaveParagraphVectorModel() throws SQLException {
        int numEpochs = 3;
        int numThreads = 40;

        SequenceIterator<VocabWord> sentenceIterator = DatabaseIteratorFactory.PatentParagraphSequenceIterator(numEpochs);

        net = new ParagraphVectors.Builder()
                .seed(41)
                .batchSize(10000)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(4)
                .layerSize(VECTOR_SIZE)
                .sampling(0.00001)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .minLearningRate(0.000001)
                .useAdaGrad(true)
                .resetModel(true)
                .minWordFrequency(5000)
                .workers(numThreads)
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
                        new CustomWordVectorListener(null,"Paragraph Vectors All Paragraphs",1000000,null,"7455590","claim","alkali_metal","device","femto","finance","touchscreen","smartphone","internet","semiconductor","artificial","intelligence")
                ))
                .iterate(sentenceIterator)
                .build();

        net.fit();
        WordVectorSerializer.writeParagraphVectors(net, allParagraphsModelFile.getAbsolutePath());
    }

    public static ParagraphVectors loadParagraphsModel() throws IOException {
        return loadModel(allParagraphsModelFile.getAbsolutePath());
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