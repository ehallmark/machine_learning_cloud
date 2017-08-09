package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/17/2017.
 */
public class ResultTypeAttribute extends StreamableAttribute<String> {
    public ResultTypeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include));
    }

    @Override
    public String getName() {
        return Constants.DOC_TYPE;
    }

    @Override
    public String getType() {
        return "keyword";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }
}
