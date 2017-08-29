package models.value_models;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;

/**
 * Created by Evan on 1/27/2017.
 */
public class MeansPresentEvaluator extends ValueAttr {

    @Override
    public String getName() {
        return Constants.MEANS_PRESENT;
    }
}
