package user_interface.ui_models.engines;

import lombok.Getter;
import lombok.Setter;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.nd4j.linalg.api.ndarray.INDArray;
import user_interface.server.SimilarPatentServer;
import models.similarity_models.AbstractSimilarityModel;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.ValueAttr;
import models.value_models.ValueMapNormalizer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;

import static j2html.TagCreator.label;

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

    static final String DEFAULT_SIMILARITY_SCRIPT = "List vector = params['_source']['vector']; " +
            "if(vector == null || params.avg_vector == null) { return 0d; }" +
            "double a = 0d;" +
            "double b = 0d;" +
            "double ab = 0d;" +
            "for(int i = 0; i < vector.size(); i++) " +
            "{" +
            "    double x = (double) vector.get(i);" +
            "    double y = (double) params.avg_vector.get(i);" +
            "    a+=(x*x);" +
            "    b+=(y*y);" +
            "    ab+=(x*y);" +
            "}" +
            "if(a != 0d && b != 0d) {" +
            "    return ab/(Math.sqrt(a)*Math.sqrt(b));" +
            "} else {" +
            "    return 0d;" +
            "}";

    @Override
    public Object attributesFor(Collection portfolio, int limit) {
        return null;
    }
}
