package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class InventorName extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.INVENTOR_HARMONIZED;
    }
}
