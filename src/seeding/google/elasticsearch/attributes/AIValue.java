package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.RangeAttribute;

public class AIValue extends DoubleAttribute implements RangeAttribute {
    @Override
    public String getName() {
        return Attributes.AI_VALUE;
    }

    @Override
    public Number min() {
        return 0;
    }

    @Override
    public Number max() {
        return 100;
    }

    @Override
    public int nBins() {
        return 5;
    }

    @Override
    public Object missing() {
        return null;
    }

    @Override
    public String valueSuffix() {
        return "%";
    }
}
