package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class Lapsed extends BooleanAttribute {
    @Override
    public String getName() {
        return Attributes.LAPSED;
    }
}
