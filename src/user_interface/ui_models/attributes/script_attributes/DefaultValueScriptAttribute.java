package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

/**
 * Created by ehallmark on 7/20/17.
 */
public abstract class DefaultValueScriptAttribute extends AggregateScriptAttribute {
    public DefaultValueScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes, String fieldName, String defaultVal) {
        super(filterTypes,fieldName,defaultVal, "value");
    }

    @Override
    public String getName() {
        return fieldName;
    }

}
