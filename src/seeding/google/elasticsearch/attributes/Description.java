package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

public class Description extends NonStoredTextAttribute {
    @Override
    public String getName() {
        return Attributes.DESCRIPTION;
    }
}
