package user_interface.ui_models.engines;

import data_pipeline.helpers.CombinedModel;
import j2html.tags.Tag;
import models.similarity_models.combined_similarity_model.*;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingModel;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import seeding.google.postgres.Util;
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
    private static CombinedCPC2Vec2VAEEncodingModel encodingModelOld;
    private static RNNTextEncodingModel encodingModel;
    private static Word2Vec word2Vec;
    private static final int maxNumSamples = 128;

    private boolean loadVectors;
    private boolean oldModel;
    private static final Function<Collection<String>,INDArray> inputToVectorFunction = inputs -> {
        synchronized (TextSimilarityEngine.class) {
            if (encodingModel == null) {
                RNNTextEncodingPipelineManager rnnTextEncodingPipelineManager = RNNTextEncodingPipelineManager.getOrLoadManager(true);
                rnnTextEncodingPipelineManager.initModel(false);

                encodingModel = (RNNTextEncodingModel)rnnTextEncodingPipelineManager.getModel();
                word2Vec = rnnTextEncodingPipelineManager.getWord2Vec();
            }
            String[] words = inputs.stream().filter(input->input!=null).flatMap(input-> Stream.of(Util.textToWordFunction.apply(input))).filter(w->w!=null&&w.length()>0).toArray(s->new String[s]);
            if(words.length==0) return null;
            return encodingModel.encode(textToInputVector(words));
        }
    };

    @Deprecated
    public TextSimilarityEngine(boolean loadVectors) {
        super();
        this.oldModel=true;
        this.loadVectors=loadVectors;
        if(loadVectors)loadSimilarityNetworks();
    }

    public TextSimilarityEngine() {
        super(inputToVectorFunction);
        this.loadVectors=true;
        this.oldModel=false;
        if(loadVectors) inputToVectorFunction.apply(Collections.emptyList());
    }


    private void loadSimilarityNetworks() {
        synchronized (TextSimilarityEngine.class) {
            if (encodingModelOld == null) {
                CombinedCPC2Vec2VAEEncodingPipelineManager combinedSimilarityPipelineManager = CombinedCPC2Vec2VAEEncodingPipelineManager.getOrLoadManager(true);
                combinedSimilarityPipelineManager.initModel(false);

                encodingModelOld = (CombinedCPC2Vec2VAEEncodingModel)combinedSimilarityPipelineManager.getModel();
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
            avg = encodeTextOld(text.split("\\s+"));
            if(avg==null) throw new RuntimeException("Unable to find similarity from provided text.");
        }
        System.out.println("Running text similarity model...");
    }

    public INDArray encodeTextOld(String[] text) {
        if(encodingModelOld==null)loadSimilarityNetworks();
        return encodingModelOld.encodeText(Arrays.asList(text),maxNumSamples);
    }

    public static INDArray textToInputVector(String[] words) {
        if(words==null||words.length<1) return null;
        words = Arrays.copyOf(words,Math.min(maxNumSamples,words.length));
        INDArray vectors = word2Vec.getWordVectors(Arrays.asList(words));
        if(vectors.rows()==0) return null;
        vectors = vectors.transpose().reshape(1,vectors.columns(),vectors.rows());
        return encodingModel.encode(vectors);
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
        if(oldModel) {
            return new TextSimilarityEngine(loadVectors);
        } else {
            return new TextSimilarityEngine();
        }
    }
}
