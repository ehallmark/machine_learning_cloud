package user_interface.ui_models.attributes.script_attributes;

import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class CountAttribute extends DefaultValueScriptAttribute {
    public CountAttribute(String name) {
        super(Arrays.asList(AbstractFilter.FilterType.Between),name,"0");
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
