package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.script_attributes.CountAttribute;

/**
 * Created by ehallmark on 6/15/17.
 */
public class NumCompDBAssignmentsAttribute extends CountAttribute {
    public NumCompDBAssignmentsAttribute() {
        super(Constants.COMPDB+"."+Constants.NUM_ASSIGNMENTS);
    }

    @Override
    public String getName() {
        return Constants.NUM_ASSIGNMENTS;
    }
}
