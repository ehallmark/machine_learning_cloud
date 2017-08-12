package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class AssignorsNestedAttribute extends NestedAttribute {

    public AssignorsNestedAttribute() {
        super(Arrays.asList(new AssignorNameAttribute(), new ExecutionDateAttribute()));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNORS;
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
