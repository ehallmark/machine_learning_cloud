package dl4j_neural_nets.vectorization;

import dl4j_neural_nets.listeners.CustomWordVectorListener;
import dl4j_neural_nets.tools.MyPreprocessor;
import dl4j_neural_nets.tools.MyTokenizerFactory;
import dl4j_neural_nets.iterators.sequences.DatabaseIteratorFactory;
import org.deeplearning4j.models.embeddings.learning.impl.elements.CBOW;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;

import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Created by ehallmark on 11/30/16.
 */
public class WordVectorModel {
    static File oldWordVectorFile = new File("data/claims.wordvectors");
    public static File wordVectorFile = new File("data/claims_2.wordvectors");
    private static TokenizerFactory tokenizerFactory = new MyTokenizerFactory();
    private static Logger log = LoggerFactory.getLogger(WordVectorModel.class);
    static {
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    private Word2Vec net;
    private int minElementFrequency = 5;
    private SequenceIterator<VocabWord> sentenceIterator;

    public WordVectorModel() throws SQLException,IOException {
        // make sure we have vocab
        sentenceIterator = DatabaseIteratorFactory.PatentSequenceIterator();
        buildModel();
    }

    private void buildModel() throws SQLException {
        net = new Word2Vec.Builder()
                .seed(41)
                .batchSize(100)
                .epochs(10)
                .windowSize(6)
                .layerSize(200)
                .sampling(0.00001)
                //.negativeSample(15.0)
                .learningRate(0.1)
                .useAdaGrad(true)
                .resetModel(true)
                .minLearningRate(0.0001)
                .elementsLearningAlgorithm(new CBOW<>())
                .minWordFrequency(minElementFrequency)
                .workers(6)
                .iterations(1)
                .tokenizerFactory(tokenizerFactory)
                .setVectorsListeners(Arrays.asList(
                        new CustomWordVectorListener(wordVectorFile,"Word Vector Model",500000,null,"claim","device","femto","finance","touchscreen","smartphone","internet","semiconductor","artificial","intelligence")
                ))
                .iterate(sentenceIterator)
                .build();
    }

    public void save() throws IOException {
        // Write word vectors to file
        WordVectorSerializer.writeFullModel(net, wordVectorFile.getAbsolutePath());
    }

    public static Word2Vec load(String filePath) throws IOException {
        Word2Vec net = WordVectorSerializer.loadFullModel(filePath);
        net.setTokenizerFactory(tokenizerFactory);
        return net;
    }

    public void train() {
        net.fit();
    }

    public static void main(String[] args) throws SQLException,IOException {
        WordVectorModel model = new WordVectorModel();
        model.train();
        model.save();
    }
}
