package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;

/**
 * Created by ehallmark on 7/20/17.
 */
public class PriorityDateAttribute extends ComputableAttribute<String> {
    public PriorityDateAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return null;
        String item = portfolio.stream().findAny().get();
        LocalDate date = Database.getPriorityDateFor(item, Database.isApplication(item));
        if(date==null) return null;
        return date.toString();
    }

    @Override
    public String getName() {
        return Constants.PRIORITY_DATE;
    }

    @Override
    public String getType() {
        return "date";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Date;
    }
}
