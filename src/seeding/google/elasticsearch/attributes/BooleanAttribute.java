package seeding.google.elasticsearch.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

public abstract class BooleanAttribute extends AbstractAttribute {
    public BooleanAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolTrue, AbstractFilter.FilterType.BoolFalse, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }
}
