package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.util.*;

/**
 * Created by ehallmark on 7/20/17.
 */
public class ExpiredAttribute extends AbstractScriptAttribute {
    public ExpiredAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse));
    }

    @Override
    public String getName() {
        return Constants.EXPIRED;
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
