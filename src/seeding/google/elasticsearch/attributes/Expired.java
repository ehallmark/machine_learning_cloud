package seeding.google.elasticsearch.attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Created by ehallmark on 7/20/17.
 */
public class Expired extends AbstractScriptAttribute implements ConvenienceAttribute {
    public Expired() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse));
    }

    @Override
    public String getName() {
        return Attributes.EXPIRED;
    }

    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public Map<String, Object> getParams() {
        return Collections.singletonMap(TREND_PARAM_EXPRESSION,getCurrentYearAndMonth());
    }

    @Override
    public Script getScript(boolean requireParams, boolean idOnly) {
        if(idOnly) return new Script(ScriptType.STORED,"expression",getFullName(),getParams());
        String script = "doc['"+Constants.LAPSED+"'].value > 0.5 ? 1 : ("+getRemainingLifeQuery(true)+" <= 0)";
        return new Script(ScriptType.INLINE,"expression",script, getParams());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

}
