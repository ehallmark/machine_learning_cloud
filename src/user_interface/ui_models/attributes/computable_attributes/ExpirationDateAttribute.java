package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;

/**
 * Created by ehallmark on 7/20/17.
 */
public class ExpirationDateAttribute extends PriorityDateAttribute {

    @Override
    public String getName() {
        return Constants.EXPIRATION_DATE;
    }

}
