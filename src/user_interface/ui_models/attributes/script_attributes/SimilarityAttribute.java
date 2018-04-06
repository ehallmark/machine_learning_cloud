package user_interface.ui_models.attributes.script_attributes;

import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.engines.AbstractSimilarityEngine;
import user_interface.ui_models.engines.SimilarityEngineController;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.stream.Collectors;

import static user_interface.server.SimilarPatentServer.PRE_FILTER_ARRAY_FIELD;
import static user_interface.server.SimilarPatentServer.extractArray;

/**
 * Created by ehallmark on 6/15/17.
 */ // TODO REMOVE THIS CLASS AND REPLACE WITH FAST_SIMILARITY_ATTRIBUTE
public class SimilarityAttribute extends AbstractScriptAttribute implements DependentAttribute<AbstractScriptAttribute>, RangeAttribute {
    public static final int vectorSize = DeepCPCVariationalAutoEncoderNN.VECTOR_SIZE;
    public static final int dimensionsForSort = vectorSize;
    public static final String VECTOR_NAME = "cvec";

    public static final String EXPRESSION_SIMILARITY_SCRIPT;
    public static final String EXPRESSION_SIMILARITY_SCRIPT_FOR_SORT;
    static {
        StringJoiner cosSort = new StringJoiner("+", "doc['"+VECTOR_NAME+".0'].empty ? 0.0 : (100.0 * (", "))");
        StringJoiner cos = new StringJoiner("+", "doc['"+VECTOR_NAME+".0'].empty ? 0.0 : (100.0 * (", "))");
        for (int i = 0; i < vectorSize; i++) {
            String inner = "(doc['"+VECTOR_NAME+"." + i + "'].value*avg_vector" + i + ")";
            cos.add(inner);
            if(i<dimensionsForSort) {
                cosSort.add(inner);
            }
        }
        EXPRESSION_SIMILARITY_SCRIPT = cos.toString();
        EXPRESSION_SIMILARITY_SCRIPT_FOR_SORT = cosSort.toString();
    }
    protected List<INDArray> simVectors;


    @Override
    public Map<String, Object> getParams() {
        if(simVectors==null||simVectors.size()==0) return Collections.emptyMap();

        Map<String, Object> params = new HashMap<>();
        INDArray avgVector = simVectors.size() == 1 ? simVectors.get(0) : Nd4j.vstack(simVectors).mean(0);
        double[] data = avgVector.data().asDouble();
        for(int i = 0; i < vectorSize; i++) {
            params.put("avg_vector"+i,data[i]);
        }
        return params;
    }

    @Override
    public Script getScript(boolean requireParams, boolean idOnly) {
        return getScriptHelper(EXPRESSION_SIMILARITY_SCRIPT,requireParams,idOnly);
    }


    // WARNING THIS IS JUST AN APPROXIMATION...
    @Override
    public Script getSortScript() {
        return getScriptHelper(EXPRESSION_SIMILARITY_SCRIPT_FOR_SORT,true,true);
    }

    private Script getScriptHelper(String script, boolean requireParams, boolean idOnly) {
        if(idOnly&&simVectors!=null&&simVectors.size()>0) return new Script(ScriptType.STORED,"expression",getFullName(),getParams());

        Script searchScript = null;
        if((!requireParams)||(simVectors!=null&&simVectors.size()>0)) {
            System.out.println("Found similarity vectors!!!");

            searchScript = new Script(
                    ScriptType.INLINE,
                    "expression",
                    script,
                    getParams()
            );
        }
        return searchScript;
    }

    public SimilarityAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.GreaterThan));
    }

    @Override
    public String getType() {
        return "double";
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        List<String> similarityEngines = extractArray(req, PRE_FILTER_ARRAY_FIELD);
        List<AbstractSimilarityEngine> relevantEngines = SimilarityEngineController.getAllEngines().stream().filter(engine->similarityEngines.contains(engine.getName())).collect(Collectors.toList());
        simVectors = relevantEngines.stream().map(engine->{
            engine = engine.dup();
            engine.extractRelevantInformationFromParams(req);
            return engine.getAvg();
        }).filter(avg->avg!=null).collect(Collectors.toList());
    }

    @Override
    public AbstractScriptAttribute dup() {
        return new SimilarityAttribute();
    }

    @Override
    public String getName() {
        return Constants.SIMILARITY;
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
    }

    @Override
    public Number min() {
        return -100;
    }

    @Override
    public Number max() {
        return 100;
    }

    @Override
    public int nBins() {
        return 10;
    }

    @Override
    public String valueSuffix() {
        return "%";
    }
}
