package user_interface.ui_models.engines;

import data_pipeline.helpers.CombinedModel;
import j2html.tags.Tag;
import models.similarity_models.combined_similarity_model.*;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.TEXT_TO_SEARCH_FOR;
import static user_interface.server.SimilarPatentServer.extractString;

/**
 * Created by ehallmark on 2/28/17.
 */
public class TextSimilarityEngine extends AbstractSimilarityEngine {
    private static CombinedCPC2Vec2VAEEncodingModel encodingModel;
    private static final int maxNumSamples = 30;

    public TextSimilarityEngine() {
        loadSimilarityNetworks();
    }

    private void loadSimilarityNetworks() {
        synchronized (TextSimilarityEngine.class) {
            if (encodingModel == null) {
                CombinedCPC2Vec2VAEEncodingPipelineManager combinedSimilarityPipelineManager = CombinedCPC2Vec2VAEEncodingPipelineManager.getOrLoadManager(true);
                combinedSimilarityPipelineManager.initModel(false);

                encodingModel = (CombinedCPC2Vec2VAEEncodingModel)combinedSimilarityPipelineManager.getModel();
            }
        }
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        // get input data
        return null;
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        String text = extractString(req, TEXT_TO_SEARCH_FOR, "").toLowerCase().trim();
        if(text.length()>0) {
            avg = encodeText(text.split("\\s+"));
            if(avg==null) throw new RuntimeException("Unable to find similarity from provided text.");
        }
        System.out.println("Running text similarity model...");
    }

    public INDArray encodeText(String[] text) {
        return encodingModel.encodeText(Arrays.asList(text),maxNumSamples);
    }

    @Override
    public String getId() {
        return TEXT_TO_SEARCH_FOR;
    }

    @Override
    public String getName() {
        return Constants.TEXT_SIMILARITY;
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","Enter any text or document").withId(getId()).withName(getId())
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
