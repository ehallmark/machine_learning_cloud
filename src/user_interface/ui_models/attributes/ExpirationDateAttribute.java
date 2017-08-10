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
 * Created by ehallmark on 7/20/17.
 */
public class ExpirationDateAttribute extends PriorityDateAttribute {

    @Override
    public String getName() {
        return Constants.EXPIRATION_DATE;
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
