package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class InventionTitleAttribute extends AbstractAttribute<String> {
    public InventionTitleAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.AdvancedKeyword));
    }

    @Override
    public String getName() {
        return Constants.INVENTION_TITLE;
    }

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.AdvancedKeyword;
    }
}
