package user_interface.ui_models.engines;

import com.google.gson.Gson;
import j2html.tags.Tag;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingModel;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.google.postgres.Util;
import spark.Request;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.TEXT_TO_SEARCH_FOR;

/**
 * Created by ehallmark on 2/28/17.
 */
public class TextSimilarityEngine extends AbstractSimilarityEngine {
    private static final Function<Collection<String>,INDArray> inputToVectorFunction = inputs -> {
        // send request to http://127.0.0.1:5000/encode?text={text}
        try {
            URL url = new URL("http://127.0.0.1:5000/encode");
            Map<String, Object> params = new LinkedHashMap<>();
            if(inputs==null || inputs.isEmpty()) return null;
            params.put("text", String.join(" ", inputs));

            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
                postData.append('=');
                postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
            conn.setDoOutput(true);
            conn.getOutputStream().write(postDataBytes);

            Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

            StringBuilder sb = new StringBuilder();
            for (int c; (c = in.read()) >= 0;)
                sb.append((char)c);
            String response = sb.toString();
            List<Double> results = (List<Double>)new Gson().fromJson(response, List.class);
            if(results!=null) {
                return Nd4j.create(results.stream().mapToDouble(d->d).toArray());
            }
            return null;
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    };



    public TextSimilarityEngine() {
        super(inputToVectorFunction);
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
