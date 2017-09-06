package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.computable_attributes.GatherStageAttribute;
import user_interface.ui_models.attributes.computable_attributes.GatherTechnologyAttribute;
import user_interface.ui_models.attributes.computable_attributes.GatherValueAttribute;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class GatherNestedAttribute extends NestedAttribute {

    public GatherNestedAttribute() {
        super(Arrays.asList(new GatherValueAttribute(), new GatherStageAttribute(), new GatherTechnologyAttribute()));
        this.isObject=true;
    }

    @Override
    public String getName() {
        return Constants.GATHER;
    }

    @Override
    public boolean isNotYetImplemented() {
        return true;
    }
}
