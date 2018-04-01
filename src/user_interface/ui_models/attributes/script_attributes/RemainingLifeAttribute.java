package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
    public Number min() {
        return 0;
    }

    @Override
    public Number max() {
        return 20;
    }

    @Override
    public int nBins() {
        return 4;
    }

    @Override
    public String valueSuffix() {
        return " Years";
    }
}
