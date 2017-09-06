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
    }

    @Override
    public String getName() {
        return Constants.COMPDB;
    }

    // TODO IMPLEMENT!!!!
    @Override
    public boolean isNotYetImplemented() {
        return true;
    }
}
