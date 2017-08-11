package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 7/20/17.
 */
public class ExpiredAttribute extends ComputableAttribute<Boolean> {
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
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }
}
