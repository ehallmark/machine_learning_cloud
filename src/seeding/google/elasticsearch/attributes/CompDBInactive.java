package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CompDBInactive extends BooleanAttribute {
    @Override
    public String getName() {
        return Attributes.COMPDB_INACTIVE;
    }
}
