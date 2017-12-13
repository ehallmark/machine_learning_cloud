package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.script_attributes.CountAttribute;

/**
 * Created by ehallmark on 6/15/17.
 */
public class NumBackwardCitationsAttribute extends CountAttribute {
    public NumBackwardCitationsAttribute() {
        super(Constants.NUM_BACKWARD_CITATIONS);
    }
}
