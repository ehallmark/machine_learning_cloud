package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class ClaimTextAttribute extends AbstractAttribute {
    public ClaimTextAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.AdvancedKeyword));
    }

    @Override
    public String getName() {
        return Constants.CLAIM;
    }

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.AdvancedKeyword;
    }
}
