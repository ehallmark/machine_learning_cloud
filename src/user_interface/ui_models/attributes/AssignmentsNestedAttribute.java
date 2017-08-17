package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class AssignmentsNestedAttribute extends NestedAttribute {

    public AssignmentsNestedAttribute() {
        super(Arrays.asList(new LatestAssigneeNestedAttribute(), new ExecutionDateAttribute(), new AssignorsNestedAttribute(), new ExecutionDateAttribute(), new AssetNumberAttribute()));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNMENTS;
    }

}
