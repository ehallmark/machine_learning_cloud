package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 7/20/17.
 */
public class ExpiredAttribute extends ComputableAttribute<Boolean> {
    public ExpiredAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse));
    }

    @Override
    public String getName() {
        return Constants.EXPIRED;
    }

    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

    @Override
    public Boolean attributesFor(Collection<String> items, int limit) {
        if(items.isEmpty()) return null;
        String item = items.stream().findAny().get();
        return Database.isExpired(item) || Database.getPriorityDateFor(item, Database.isApplication(item)).plusYears(20).isBefore(LocalDate.now());
    }
}
