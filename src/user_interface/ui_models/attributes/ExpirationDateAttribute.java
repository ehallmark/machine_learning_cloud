package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.PriorityDateAttribute;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Created by ehallmark on 7/20/17.
 */
public class ExpirationDateAttribute extends PriorityDateAttribute {

    @Override
    public String getName() {
        return Constants.EXPIRATION_DATE;
    }


}
