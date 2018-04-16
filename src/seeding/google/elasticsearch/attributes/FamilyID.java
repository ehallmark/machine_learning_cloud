package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

public class FamilyID extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.FAMILY_ID;
    }
}
