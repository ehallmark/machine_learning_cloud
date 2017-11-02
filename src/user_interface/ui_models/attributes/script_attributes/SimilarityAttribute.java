package user_interface.ui_models.attributes.script_attributes;

import models.similarity_models.AbstractSimilarityModel;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.engines.AbstractSimilarityEngine;
import user_interface.ui_models.engines.SimilarityEngineController;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 6/15/17.
 */
public class SimilarityAttribute extends AbstractScriptAttribute implements DependentAttribute<AbstractScriptAttribute> {
    public static final int vectorSize = 32;

    public static final String EXPRESSION_SIMILARITY_SCRIPT;
    public static final String COSINE_SIM;
    public static final String DISTANCE_SIM;
    static {
        StringJoiner cos = new StringJoiner("+","doc['vector_obj.0'].empty ? _score : ((",") * _score)");
        for(int i = 0; i < vectorSize; i++) {
            cos.add("(doc['vector_obj."+i+"'].value*avg_vector"+i+")");
        }
        COSINE_SIM=cos.toString();
        StringJoiner dist = new StringJoiner("+","doc['vector_obj.0'].empty ? _score : 1.0-(((",") * _score / "+vectorSize+"))");
        for(int i = 0; i < vectorSize; i++) {
            dist.add("((doc['vector_obj."+i+"'].value-avg_vector"+i+")*(doc['vector_obj."+i+"'].value-avg_vector"+i+"))");
        }
        DISTANCE_SIM=dist.toString();
        System.out.println("Inverse distance script: "+DISTANCE_SIM);
        System.out.println("Cosine distance script: "+COSINE_SIM);

        boolean useCosineSim = false;
        if(useCosineSim) {
            EXPRESSION_SIMILARITY_SCRIPT = COSINE_SIM;
        } else {
            EXPRESSION_SIMILARITY_SCRIPT = DISTANCE_SIM;
        }
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
        }
        return searchScript;
    }

    public SimilarityAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.GreaterThan));
    }

    @Override
    public String getType() {
        return "float";
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        String similarityModelStr = Constants.PARAGRAPH_VECTOR_MODEL;
        RecursiveTask<AbstractSimilarityModel> finderPrototype = similarityModelMap.get(similarityModelStr);

        List<String> similarityEngines = extractArray(req, PRE_FILTER_ARRAY_FIELD);
        List<AbstractSimilarityEngine> relevantEngines = SimilarityEngineController.getEngines().stream().filter(engine->similarityEngines.contains(engine.getName())).collect(Collectors.toList());
        simVectors = relevantEngines.stream().map(engine->{
            engine = engine.dup();
            engine.setSimilarityModel(finderPrototype);
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
}
