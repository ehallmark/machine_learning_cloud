package user_interface.ui_models.attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 6/15/17.
 */
public class LengthOfSmallestIndependentClaimAttribute extends StreamableAttribute<Integer> {
    public LengthOfSmallestIndependentClaimAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public String getName() {
        return Constants.SMALLEST_INDEPENDENT_CLAIM_LENGTH;
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
