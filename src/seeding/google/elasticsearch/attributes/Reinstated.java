package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class Reinstated extends BooleanAttribute {
    @Override
    public String getName() {
        return Attributes.REINSTATED;
    }
}
