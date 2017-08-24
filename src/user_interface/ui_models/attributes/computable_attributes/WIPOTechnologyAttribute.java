package user_interface.ui_models.attributes.computable_attributes;

import models.classification_models.WIPOHelper;
import j2html.tags.Tag;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/18/2017.
 */
public class WIPOTechnologyAttribute extends AbstractAttribute {
    public WIPOTechnologyAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }

    @Override
    public String getName() {
        return Constants.WIPO_TECHNOLOGY;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public Collection<String> getAllValues() {
        return WIPOHelper.getOrderedClassifications();
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }
}
