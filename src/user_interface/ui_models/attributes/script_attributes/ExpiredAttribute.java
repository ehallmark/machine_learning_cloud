package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
    public QueryBuilder getSortScript() {
        return null;
    }

    @Override
    public Script getScript() {
        return new Script(ScriptType.INLINE,"expression","doc['"+Constants.LAPSED+"'].value > 0.5 ? 1 : (("+String.valueOf(millisecondsToday)+"-"+getPriorityDateField()+"+"+getTermExtensionMillis()+") <= 0)", new HashMap<>());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

}
