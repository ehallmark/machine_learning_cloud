package seeding.google.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

public class PriorityDate extends AbstractAttribute {
    public PriorityDate() {
        super(Arrays.asList(AbstractFilter.FilterType.Between, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
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
