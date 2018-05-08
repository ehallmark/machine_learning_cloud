package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CodeCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.CODE_COUNT;
    }
}
