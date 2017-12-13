package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.script_attributes.CountAttribute;

/**
 * Created by ehallmark on 6/15/17.
 */
public class NumAssetsAttribute extends CountAttribute {
    public NumAssetsAttribute() {
        super(Constants.COMPDB+"."+Constants.NUM_ASSETS);
    }

    @Override
    public String getName() {
        return Constants.NUM_ASSETS;
    }
}
