package seeding.google.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

public class Category extends AbstractAttribute {
    public Category() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getName() {
        return Constants.CATEGORY;
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
