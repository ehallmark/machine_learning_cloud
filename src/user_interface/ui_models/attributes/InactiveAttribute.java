package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 7/20/17.
 */
public class InactiveAttribute extends AbstractAttribute {
    public InactiveAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse, AbstractFilter.FilterType.BoolTrue));
    }

    @Override
    public String getName() {
        return Constants.INACTIVE_DEAL;
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
