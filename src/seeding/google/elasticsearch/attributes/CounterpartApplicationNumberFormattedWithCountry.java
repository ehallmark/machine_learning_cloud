package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collections;

public class CounterpartApplicationNumberFormattedWithCountry extends AbstractAttribute {
    public CounterpartApplicationNumberFormattedWithCountry() {
        super(Collections.emptyList());
    }

    @Override
    public String getName() {
        return Attributes.COUNTERPART_APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY;
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

}
