package user_interface.ui_models.engines;

import j2html.tags.Tag;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingModel;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.google.postgres.Util;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.TEXT_TO_SEARCH_FOR;

/**
 * Created by ehallmark on 2/28/17.
 */
public class TextSimilarityEngine extends AbstractSimilarityEngine {
    private static RNNTextEncodingModel encodingModel;
    private static Word2Vec word2Vec;
    private static final int maxNumSamples = 128;

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



    public TextSimilarityEngine() {
        super(inputToVectorFunction);
       // if(loadVectors) inputToVectorFunction.apply(Collections.emptyList());
    }

    @Override
    public AbstractSimilarityEngine clone() {
        return dup();
    }


    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        // get input data
        return null;
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
        return new TextSimilarityEngine();
    }
}
