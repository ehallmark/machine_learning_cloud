package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class ClaimsNestedAttribute extends NestedAttribute {
    public ClaimsNestedAttribute() {
        super(Arrays.asList(new ClaimNumberAttribute(), new ClaimTextAttribute(), new ClaimLengthAttribute(), new ParentClaimNumAttribute(), new IndependentClaimAttribute()));
    }

    @Override
    public String getName() {
        return Constants.CITATIONS;
    }

    @Override
    public String getType() {
        return "nested";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.NestedObject;
    }
}
