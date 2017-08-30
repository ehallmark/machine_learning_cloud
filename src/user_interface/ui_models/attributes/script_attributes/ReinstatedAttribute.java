package user_interface.ui_models.attributes.script_attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class ReinstatedAttribute extends DefaultValueScriptAttribute {
    public ReinstatedAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolTrue, AbstractFilter.FilterType.BoolFalse), Constants.REINSTATED,"0");
    }
    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }


}
