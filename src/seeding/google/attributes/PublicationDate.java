package seeding.google.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

public class PublicationDate extends AbstractAttribute {
    public PublicationDate() {
        super(Arrays.asList(AbstractFilter.FilterType.Between, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getName() {
        return Constants.PUBLICATION_DATE;
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
