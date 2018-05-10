package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public class RemainingLifeAttribute extends AbstractScriptAttribute implements RangeAttribute{

    public RemainingLifeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public String getName() {
        return Constants.REMAINING_LIFE;
    }

    @Override
    public String getType() {
        return "integer";
    }

    @Override
    public Map<String, Object> getParams() {
        return Collections.singletonMap(TREND_PARAM_EXPRESSION,getCurrentYearAndMonth());
    }

    @Override
    public Script getScript(boolean requireParams, boolean idOnly) {
        if(idOnly) return new Script(ScriptType.STORED,"expression",getFullName(),getParams());
        String script = "doc['"+Constants.LAPSED+"'].value > 0.5 ? 0 : ("+getRemainingLifeQuery(true)+") > 0 ? "+getRemainingLifeQuery(true)+" : 0";
        return new Script(ScriptType.INLINE,"expression",script, getParams());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }


    @Override
    public List<Pair<Number, Number>> getRanges() {
        return Arrays.asList(
                new Pair<>(0,5),
                new Pair<>(5,10),
                new Pair<>(10,15),
                new Pair<>(15,20)
        );
    }

    @Override
    public Object missing() {
        return 0;
    }

    @Override
    public String valueSuffix() {
        return " Years";
    }
}
