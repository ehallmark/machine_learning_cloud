package user_interface.ui_models.attributes.script_attributes;

import models.similarity_models.AbstractSimilarityModel;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.engines.AbstractSimilarityEngine;
import user_interface.ui_models.engines.SimilarityEngineController;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.stream.Collectors;

import static user_interface.server.SimilarPatentServer.*;
import static user_interface.server.SimilarPatentServer.SIMILARITY_ENGINES_ARRAY_FIELD;

/**
 * Created by ehallmark on 6/15/17.
 */
public class SimilarityAttribute extends AbstractScriptAttribute {
    public static final int vectorSize = 30; // test
    public static final String DEFAULT_SIMILARITY_SCRIPT = "" +
            "if(doc['vector_obj.0'].value == null || params.avg_vector == null) { return 0f; }" +
            "float ab = 0f;" +
            "int length = params.avg_vector.length;" +
            "for(int i = 0; i < length; i++) {" +
            "    ab+=params.avg_vector[i] * (float) doc['vector_obj.'+i].value;" +
            "}" +
            "return ab * 100;";

    public static String EXPRESSION_SIMILARITY_SCRIPT;
    static {
        StringJoiner sj = new StringJoiner("+","doc['vector_obj.0'].empty ? 0 : ((",") * 100.0)");
        for(int i = 0; i < vectorSize; i++) {
            sj.add("(doc['vector_obj."+i+"'].value*avg_vector"+i+")");
        }
        EXPRESSION_SIMILARITY_SCRIPT=sj.toString();
        System.out.println("New similarity script: "+EXPRESSION_SIMILARITY_SCRIPT);
    }
    protected List<INDArray> simVectors;


    @Override
    public Script getScript() {
        Script searchScript = null;
        if(simVectors!=null&&simVectors.size()>0) {
            System.out.println("Found similarity vectors!!!");
            Map<String, Object> params = new HashMap<>();
            INDArray avgVector = simVectors.size() == 1 ? simVectors.get(0) : Nd4j.vstack(simVectors).mean(0);
            float[] data = avgVector.data().asFloat();
            for(int i = 0; i < vectorSize; i++) {
                params.put("avg_vector"+i,data[i]);
            }
            searchScript = new Script(
                    ScriptType.INLINE,
                    "expression",
                    EXPRESSION_SIMILARITY_SCRIPT,
                    params
            );
        } else {
            System.out.println("No similarity vectors found :(");
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
        String similarityModelStr = Constants.PARAGRAPH_VECTOR_MODEL;
        AbstractSimilarityModel finderPrototype = similarityModelMap.get(similarityModelStr);

        List<String> similarityEngines = extractArray(req, SIMILARITY_ENGINES_ARRAY_FIELD);
        List<AbstractSimilarityEngine> relevantEngines = SimilarityEngineController.getEngines().stream().filter(engine->similarityEngines.contains(engine.getName())).collect(Collectors.toList());
        simVectors = relevantEngines.stream().map(engine->{
            engine.setSimilarityModel(finderPrototype);
            engine.extractRelevantInformationFromParams(req);
            return engine.getAvg();
        }).filter(avg->avg!=null).collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return Constants.SIMILARITY;
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
    }
}
