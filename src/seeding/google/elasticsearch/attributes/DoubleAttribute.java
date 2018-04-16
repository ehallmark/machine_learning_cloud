package seeding.google.elasticsearch.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

public abstract class DoubleAttribute extends AbstractAttribute {
    public DoubleAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getType() {
        return "double";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
    }
}
