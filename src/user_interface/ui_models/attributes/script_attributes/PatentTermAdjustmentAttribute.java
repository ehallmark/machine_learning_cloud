package user_interface.ui_models.attributes.script_attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 7/20/17.
 */
public class PatentTermAdjustmentAttribute extends DefaultValueScriptAttribute {
    public PatentTermAdjustmentAttribute() {
        super(Collections.emptyList(), Constants.PATENT_TERM_ADJUSTMENT,"0");
    }

    @Override
    public String getType() {
        return "integer";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }
}
