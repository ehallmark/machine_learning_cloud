package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.meta_attributes.MetaComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 7/20/17.
 */
public class ExpiredAttribute extends ComputableAttribute<Boolean> {
    public ExpiredAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse));
    }


    @Override
    public Boolean handleIncomingData(String name, Map<String, Object> data, boolean isApplication) {
        return null;
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
}
