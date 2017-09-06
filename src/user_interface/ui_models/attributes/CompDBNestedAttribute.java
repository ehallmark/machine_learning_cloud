package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.*;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class CompDBNestedAttribute extends NestedAttribute {

    public CompDBNestedAttribute() {
        super(Arrays.asList(new CompDBTechnologyAttribute(),new CompDBDealIDAttribute()));
        this.isObject=true; // TODO Remove this after remapping
    }

    @Override
    public String getName() {
        return Constants.COMPDB;
    }

    public boolean isNotYetImplemented() {
        return true;
    }
}
