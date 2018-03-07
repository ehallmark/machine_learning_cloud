package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 7/20/17.
 */
public class AcquisitionAttribute extends AbstractAttribute {
    public AcquisitionAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse, AbstractFilter.FilterType.BoolTrue));
    }

    @Override
    public String getName() {
        return Constants.ACQUISITION_DEAL;
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
