package user_interface.ui_models.engines;

import com.google.gson.Gson;
import j2html.tags.Tag;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
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

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.TEXT_TO_SEARCH_FOR;
import static user_interface.server.SimilarPatentServer.extractString;

/**
 * Created by ehallmark on 2/28/17.
 */
public class TextSimilarityEngine extends AbstractSimilarityEngine {
    public static final Function<Collection<String>,INDArray> inputToVectorFunction = inputs -> {
        // send request to http://127.0.0.1:5000/encode?text={text}
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            if(inputs==null || inputs.isEmpty()) return null;
            params.put("text", String.join(" ", inputs));
            StringBuilder stringBuilder = new StringBuilder("http://127.0.0.1:5000/encode");
            stringBuilder.append("?text=");
            stringBuilder.append(URLEncoder.encode(String.join(" ", inputs), "UTF-8"));
            URL url = new URL(stringBuilder.toString());

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

            StringBuilder sb = new StringBuilder();
            for (int c; (c = in.read()) >= 0;)
                sb.append((char)c);
            String response = sb.toString();
            List<Double> results = new Gson().fromJson(response, List.class);
            System.out.println("Results: "+results);
            if(results!=null&&results.size()>0) {
                return Nd4j.create(results.stream().mapToDouble(d->d).toArray());
            } else {
                throw new RuntimeException("Unable to compute similarity for text: "+String.join("; ", inputs));
            }
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
    protected Collection<String> getInputsToSearchFor(Request req) {
        System.out.println("Collecting text similarity inputs to search for...");
        // get input data
        String text = extractString(req, getId(), "");
        System.out.println("Found text: "+text);
        if(text==null||text.isEmpty()) return null;
        return Collections.singletonList(text.toLowerCase());
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
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
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
