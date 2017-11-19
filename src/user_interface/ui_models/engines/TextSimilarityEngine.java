package user_interface.ui_models.engines;

import j2html.tags.Tag;
import models.similarity_models.AbstractSimilarityModel;
import models.similarity_models.DefaultSimilarityModel;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_to_cpc_encoding_model.WordToCPCEncodingNN;
import models.similarity_models.word_to_cpc_encoding_model.WordToCPCIterator;
import models.similarity_models.word_to_cpc_encoding_model.WordToCPCPipelineManager;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class TextSimilarityEngine extends AbstractSimilarityEngine {
    private static RecursiveTask<Map<String,Integer>> wordIdxMap;
    private static RecursiveTask<MultiLayerNetwork> net;
    static {
        String modelName = WordToCPCPipelineManager.MODEL_NAME;
        String cpcEncodingModelName = CPCVAEPipelineManager.MODEL_NAME;
        CPCVAEPipelineManager cpcEncodingPipelineManager = new CPCVAEPipelineManager(cpcEncodingModelName);
        WordToCPCPipelineManager pipelineManager = new WordToCPCPipelineManager(modelName, cpcEncodingPipelineManager);

        net = new RecursiveTask<MultiLayerNetwork>() {
            @Override
            protected MultiLayerNetwork compute() {
                WordToCPCEncodingNN model = new WordToCPCEncodingNN(pipelineManager, modelName);
                try {
                    model.loadBestModel();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return model.getNet();
            }
        };
        net.fork();

        wordIdxMap = new RecursiveTask<Map<String, Integer>>() {
            @Override
            protected Map<String, Integer> compute() {
                return pipelineManager.loadVocabMap();
            }
        };
        wordIdxMap.fork();
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        String text = extractString(req, TEXT_TO_SEARCH_FOR, "").toLowerCase().trim();
        Collection<String> keywords = Arrays.asList(text.split("\\s+"));
        System.out.println("Found "+keywords.size()+" patents...");
        return keywords;
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        List<String> resultTypes = SimilarPatentServer.extractArray(req,  Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        inputs = getInputsToSearchFor(req,resultTypes);
        if(inputs!=null&&inputs.size()>0) {
            Stream<Collection<String>> wordStream = Stream.of(inputs);
            // get the input to the word to cpc network
            INDArray bowVector = WordToCPCIterator.createBagOfWordsVector(wordStream,wordIdxMap.join(),1);
            if(bowVector!=null&&bowVector.sumNumber().doubleValue()>0) {
                // encode using word to cpc network
                avg = net.join().activateSelectedLayers(0, net.join().getnLayers() - 1, bowVector);
            }
        }
    }


    @Override
    public String getName() {
        return Constants.TEXT_SIMILARITY;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","Enter any text or document").withId(TEXT_TO_SEARCH_FOR).withName(TEXT_TO_SEARCH_FOR)
        );
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public AbstractSimilarityEngine dup() {
        return new TextSimilarityEngine();
    }
}
