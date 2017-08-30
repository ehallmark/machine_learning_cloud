package user_interface.ui_models.attributes.script_attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;

/**
 * Created by Evan on 8/11/2017.
 */
public class LapsedAttribute extends DefaultValueScriptAttribute {
    public LapsedAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolTrue,AbstractFilter.FilterType.BoolFalse),"0");
    }

    @Override
    public String getName() {
        return Constants.LAPSED;
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
