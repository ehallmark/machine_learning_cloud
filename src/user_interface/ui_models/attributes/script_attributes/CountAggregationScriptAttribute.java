package user_interface.ui_models.attributes.script_attributes;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by ehallmark on 6/15/17.
 */
public class CountAggregationScriptAttribute extends AggregateScriptAttribute {
    @Getter
    private String name;
    public CountAggregationScriptAttribute(String fieldName, String name) {
        super(Arrays.asList(AbstractFilter.FilterType.Between), "painless", fieldName, "0", "values.size()");
        this.name=name;
    }

    @Override
    public String getType() {
        return "integer";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

}
