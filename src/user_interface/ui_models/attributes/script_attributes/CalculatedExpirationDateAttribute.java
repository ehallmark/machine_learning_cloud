package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 7/20/17.
 */
public class CalculatedExpirationDateAttribute extends AbstractScriptAttribute {
    public CalculatedExpirationDateAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public String getName() {
        return Constants.ESTIMATED_EXPIRATION_DATE;
    }


    @Override
    public String getType() {
        return "date";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Date;
    }

    @Override
    public Map<String, Object> getParams() {
        return Collections.emptyMap();
    }

    @Override
    public Script getScript(boolean requireParams, boolean idOnly) {
        if(idOnly) return new Script(ScriptType.STORED,"expression",getFullName(),getParams());
        return new Script(ScriptType.INLINE, "expression", getCalculatedPriorityDateField() + "+" + (millisecondsPerYear*20), getParams());
    }
}
