package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Created by ehallmark on 7/20/17.
 */
public class ExpirationDateAttribute extends PriorityDateAttribute {

    @Override
    public String getName() {
        return Constants.EXPIRATION_DATE;
    }

    @Override
    public LocalDate attributesFor(Collection<String> portfolio, int limit) {
        LocalDate date = super.attributesFor(portfolio,limit);
        if(date == null) return null;
        return date.plusYears(20);
    }

}
