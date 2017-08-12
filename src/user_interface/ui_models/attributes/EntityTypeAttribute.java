package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import models.classification_models.WIPOHelper;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/17/2017.
 */
public class EntityTypeAttribute extends AbstractAttribute<String> {
    public EntityTypeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE_ENTITY_TYPE;
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

    @Override
    public Collection<String> getAllValues() {
        return Arrays.asList(Constants.SMALL,Constants.MICRO,Constants.LARGE);
    }
}
