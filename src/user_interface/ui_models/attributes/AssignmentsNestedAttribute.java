package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.LatestExecutionDateAttribute;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class AssignmentsNestedAttribute extends NestedAttribute {

    public AssignmentsNestedAttribute() {
        super(Arrays.asList(new AssigneeNameAttribute(), new AssignorNameAttribute(), new RecordedDateAttribute(), new LatestExecutionDateAttribute(), new ReelFrameAttribute(), new ConveyanceTextAttribute()));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNMENTS;
    }


}
