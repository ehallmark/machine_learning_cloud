package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class MeansPresent extends BooleanAttribute {
    @Override
    public String getName() {
        return Attributes.MEANS_PRESENT;
    }
}
