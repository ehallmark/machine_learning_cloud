package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import user_interface.ui_models.attributes.PriorityDateAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

/**
 * Created by ehallmark on 7/20/17.
 */
public class ExpirationDateAttribute extends AbstractScriptAttribute {
    public ExpirationDateAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public String getName() {
        return Constants.EXPIRATION_DATE;
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
    public QueryBuilder getSortScript() {
        return null;
    }

    @Override
    public Script getScript() {
        return new Script(ScriptType.INLINE, "expression", "("+getPriorityDateField("year")+20+").toString()+'-'+"+getPriorityDateField("monthOfYear")+"+'-'+"+getPriorityDateField("dayOfMonth"), new HashMap<>());
    }
}
