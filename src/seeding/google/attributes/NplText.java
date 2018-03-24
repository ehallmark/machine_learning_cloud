package seeding.google.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

public class NplText extends AbstractAttribute {
    public NplText() {
        super(Arrays.asList(AbstractFilter.FilterType.AdvancedKeyword, AbstractFilter.FilterType.Regexp, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getName() {
        return Constants.NPL_TEXT;
    }

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }
}
