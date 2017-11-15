package models.similarity_models.word_to_cpc_encoding_model;

import ch.qos.logback.classic.Level;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import models.similarity_models.signatures.CPCSimilarityVectorizer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/15/17.
 */
public class TestModel {

    public static void main(String[] args) throws Exception {
        String text = "Virtual reality (VR) is a computer technology that uses virtual reality headsets or multi-projected environments, sometimes in combination with physical environments or props, to generate realistic images, sounds and other sensations that simulate a user's physical presence in a virtual or imaginary environment. A person using virtual reality equipment is able to \"look around\" the artificial world, and with high quality VR move around in it and interact with virtual features or items. The effect is commonly created by VR headsets consisting of head-mounted goggles with a screen in front of the eyes, but can also be created through specially designed spaces with multiple large screens.\n" +
                "VR systems that include transmission of vibrations and other sensations to the user through a game controller or other devices are known as haptic systems. This tactile information is generally known as force feedback in medical, video gaming and military training applications. Virtual reality also refers to remote communication environments which provide a virtual presence of users with through telepresence and telexistence or the use of a virtual artifact (VA). The immersive environment can be similar to the real world in order to create a lifelike experience grounded in reality or sci-fi. Augmented reality systems may also be considered a form of VR that layers virtual information over a live camera feed into a headset, or through a smartphone or tablet device.";

        String modelName = WordToCPCPipelineManager.MODEL_NAME;
        String cpcEncodingModelName = CPCVAEPipelineManager.MODEL_NAME;

        CPCVAEPipelineManager cpcEncodingPipelineManager = new CPCVAEPipelineManager(cpcEncodingModelName);
        WordToCPCPipelineManager pipelineManager = new WordToCPCPipelineManager(modelName, cpcEncodingPipelineManager);

        WordToCPCEncodingNN model = new WordToCPCEncodingNN(pipelineManager,modelName);
        model.loadBestModel();

        Map<String,Integer> wordIdxMap = pipelineManager.getWordToIdxMap();
        MultiLayerNetwork net = model.getNet();

        CPCSimilarityVectorizer vectorizer = new CPCSimilarityVectorizer(cpcEncodingPipelineManager.loadPredictions(),false,false,false);

        List<WordFrequencyPair<String,Double>> results = predictPatentsFromText(text, wordIdxMap, net, vectorizer);

        System.out.println("Most similar patents to text: "+text);
        for(int i = 0; i < results.size(); i++) {
            System.out.println(results.get(i).toString());
        }
    }

    static List<WordFrequencyPair<String,Double>> predictPatentsFromText(String text, Map<String,Integer> wordIdxMap, MultiLayerNetwork net, CPCSimilarityVectorizer vectorizer) {
        Stream<Collection<String>> wordStream = Stream.of(Arrays.asList(text.toLowerCase().split("\\s+")));
        // get the input to the word to cpc network
        INDArray bowVector = WordToCPCIterator.createBagOfWordsVector(wordStream,wordIdxMap,1);
        // encode using word to cpc network
        INDArray encoding = net.activateSelectedLayers(0,net.getnLayers()-1,bowVector);
        // compare to asset encodings
        return vectorizer.similarTo(encoding,10);
    }
}
