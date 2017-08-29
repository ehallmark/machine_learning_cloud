package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;

/**
 * Created by ehallmark on 6/15/17.
 */
public class ParentClaimNumAttribute extends AbstractAttribute {
    public ParentClaimNumAttribute() {
        super(Collections.emptyList());
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
        return Constants.PARENT_CLAIM_NUM;
    }

}
