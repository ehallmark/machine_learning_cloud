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
    public String attributesFor(Collection<String> portfolio, int limit) {
        String item = portfolio.stream().findAny().get();
        LocalDate date = Database.getPriorityDateFor(item, Database.isApplication(item));
        if(date==null) return null;
        return date.plusYears(20).toString();
    }

}
