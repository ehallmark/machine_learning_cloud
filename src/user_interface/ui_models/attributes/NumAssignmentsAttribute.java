package user_interface.ui_models.attributes;

import lombok.Getter;
import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class NumAssignmentsAttribute extends CountAttribute {
    public NumAssignmentsAttribute(String name) {
        super(Constants.NUM_ASSIGNMENTS);
    }
}
