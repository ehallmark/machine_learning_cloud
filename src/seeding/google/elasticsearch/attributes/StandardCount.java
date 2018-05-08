package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class StandardCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.STANDARD_COUNT;
    }
}
