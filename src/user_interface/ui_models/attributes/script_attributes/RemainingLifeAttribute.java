package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

/**
 * Created by ehallmark on 6/15/17.
 */
public class RemainingLifeAttribute extends AbstractScriptAttribute {

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
    public Script getScript() {
        String script = "doc['"+Constants.LAPSED+"'].value > 0.5 ? 0 : "+getRemainingLifeQuery();
        System.out.println("Script for remaining life: "+script);
        return new Script(ScriptType.INLINE,"expression",script, new HashMap<>());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }


}
