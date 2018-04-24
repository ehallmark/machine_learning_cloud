package seeding.google.elasticsearch.attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import spark.Request;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.engines.AbstractSimilarityEngine;
import user_interface.ui_models.engines.SimilarityEngineController;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static user_interface.server.SimilarPatentServer.PRE_FILTER_ARRAY_FIELD;
import static user_interface.server.SimilarPatentServer.extractArray;

public abstract class SimilarityAttribute extends AbstractScriptAttribute implements DependentAttribute<AbstractScriptAttribute>, RangeAttribute {
    protected List<INDArray> simVectors;
    public SimilarityAttribute() {
        super(Collections.singleton(AbstractFilter.FilterType.GreaterThan));
    }

    @Override
    public String getType() {
        return "double";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
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
    public Map<String, Object> getParams() {
        if(simVectors==null||simVectors.size()==0) return Collections.emptyMap();
        Map<String, Object> params = new HashMap<>();
        INDArray avgVector = simVectors.size() == 1 ? simVectors.get(0) : Nd4j.vstack(simVectors).mean(0);
        List<Double> vector = DoubleStream.of(avgVector.data().asDouble()).mapToObj(d->d).collect(Collectors.toList());
        params.put("vector", vector);
        params.put("field", getName());
        params.put("cosine", false);
        params.put("float", true);
        params.put("scale", 100D);
        return params;
    }

    @Override
    public Script getScript(boolean requireParams, boolean idOnly) {
        return getScriptHelper(requireParams,idOnly);
    }


    // WARNING THIS IS JUST AN APPROXIMATION...
    @Override
    public Script getSortScript() {
        return getScriptHelper(true,true);
    }

    private Script getScriptHelper(boolean requireParams, boolean idOnly) {
        if(idOnly&&simVectors!=null&&simVectors.size()>0) return new Script(ScriptType.STORED,"knn",getFullName(),getParams());

        Script searchScript = null;
        if((!requireParams)||(simVectors!=null&&simVectors.size()>0)) {
            System.out.println("Found similarity vectors!!!");

            searchScript = new Script(
                    ScriptType.INLINE,
                    "knn",
                    "binary_vector_score",
                    getParams()
            );
        }
        return searchScript;
    }

    @Override
    public Number min() {
        return -100d;
    }

    @Override
    public Number max() {
        return 100d;
    }

    @Override
    public int nBins() {
        return 4;
    }

    @Override
    public Object missing() {
        return null;
    }

    @Override
    public String valueSuffix() {
        return "%";
    }
}
