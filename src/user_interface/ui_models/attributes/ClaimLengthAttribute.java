package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class ClaimLengthAttribute extends AbstractAttribute {
    public ClaimLengthAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }
    @Override
    public String getType() {
        return "integer";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

    @Override
    public String getName() {
        return Constants.CLAIM_LENGTH;
    }

}
