package seeding.google.elasticsearch.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collections;

public abstract class SimilarityAttribute extends AbstractAttribute {
    public SimilarityAttribute() {
        super(Collections.singleton(AbstractFilter.FilterType.GreaterThan));
    }

    @Override
    public String getType() {
        return "float";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
    }
}
