package seeding.google.elasticsearch.attributes;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import spark.Request;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.engines.AbstractSimilarityEngine;
import user_interface.ui_models.engines.SimilarityEngineController;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static user_interface.server.SimilarPatentServer.PRE_FILTER_ARRAY_FIELD;
import static user_interface.server.SimilarPatentServer.extractArray;

public abstract class SimilarityAttribute extends AbstractScriptAttribute implements DependentAttribute<AbstractScriptAttribute>, RangeAttribute {
    @Getter @Setter
    protected List<INDArray> simVectors;
    private Collection<String> validEngines;
    public SimilarityAttribute(@NonNull Collection<String> validEngines) {
        super(Collections.singleton(AbstractFilter.FilterType.GreaterThan));
        this.validEngines=validEngines;
    }

    @Override
    public String getType() {
        return "double";
    }

    @Override
    public abstract SimilarityAttribute clone();

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        List<String> similarityEngines = extractArray(req, PRE_FILTER_ARRAY_FIELD);
        List<AbstractSimilarityEngine> relevantEngines = SimilarityEngineController.getAllEngines().stream().filter(engine->similarityEngines.contains(engine.getName())&&validEngines.contains(engine.getName())).collect(Collectors.toList());
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
        return getScriptHelper(true,false);
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
    public List<Pair<Number, Number>> getRanges() {
        return Arrays.asList(
                new Pair<>(-100,-80),
                new Pair<>(-75,-50),
                new Pair<>(-50,-25),
                new Pair<>(-25,0),
                new Pair<>(0,25),
                new Pair<>(25,50),
                new Pair<>(50,75),
                new Pair<>(75,100)
        );
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
