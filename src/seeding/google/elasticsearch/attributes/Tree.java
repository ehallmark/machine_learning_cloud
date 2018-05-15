package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.filters.AbstractFilter;

public class Tree extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.TREE;
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }
    
}
