package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;

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

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        String date = super.attributesFor(portfolio,limit);
        if(date == null) return null;
        return LocalDate.parse(date, DateTimeFormatter.ISO_DATE).plusYears(20).format(DateTimeFormatter.ISO_DATE);
    }

}
