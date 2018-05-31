package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class Granted extends BooleanAttribute {
    @Override
    public String getName() {
        return Attributes.GRANTED;
    }
}
