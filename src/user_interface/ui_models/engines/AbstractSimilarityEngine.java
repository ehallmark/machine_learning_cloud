package user_interface.ui_models.engines;

import lombok.Getter;
import lombok.Setter;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;

import org.nd4j.linalg.api.ndarray.INDArray;
import user_interface.server.SimilarPatentServer;
import models.similarity_models.AbstractSimilarityModel;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.*;


/**
 * Created by ehallmark on 2/28/17.
 */
public abstract class AbstractSimilarityEngine implements AbstractAttribute {
    @Setter
    protected AbstractSimilarityModel similarityModel;
    protected Collection<String> inputs;
    @Getter
    protected INDArray avg;

    public AbstractSimilarityEngine(AbstractSimilarityModel similarityModel) {
        this.similarityModel=similarityModel;
    }

    protected abstract Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes);

    public void extractRelevantInformationFromParams(Request req) {
        List<String> resultTypes = SimilarPatentServer.extractArray(req, SimilarPatentServer.SEARCH_TYPE_ARRAY_FIELD);
        inputs = getInputsToSearchFor(req,resultTypes);
        if(inputs!=null&&inputs.size()>0) {
            SimilarPatentFinder finder = new SimilarPatentFinder(inputs);
            if (finder.getItemList().length > 0) {
                avg = finder.computeAvg();
            }
        }
    }

    static final String DEFAULT_SIMILARITY_SCRIPT = "String vectorStr = doc['vector_str'].value;" +
            "if(vectorStr == null || params.avg_vector == null) { return 0f; }" +
            "String[] vector = /\\,/.split(vectorStr);" +
            "if(vector.length!=150) { return 0f; }" + // REMOVE THIS!!!!!
            "float a = 0f;" +
            "float b = 0f;" +
            "float ab = 0f;" +
            "for(int i = 0; i < vector.length; i++) {" +
            "    float x = Float.parseFloat(vector[i]);" +
            "    float y = (float) params.avg_vector[i];" +
            "    a+=(x*x);" +
            "    b+=(y*y);" +
            "    ab+=(x*y);" +
            "}" +
            "if(a != 0f && b != 0f) {" +
            "    return (ab/(Math.sqrt(a)*Math.sqrt(b))) * 100f;" +
            "} else {" +
            "    return 0f;" +
            "}";

    @Override
    public Object attributesFor(Collection portfolio, int limit) {
        return null;
    }
}
