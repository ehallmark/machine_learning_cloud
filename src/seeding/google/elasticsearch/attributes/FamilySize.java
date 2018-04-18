package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class FamilySize extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.FAMILY_SIZE;
    }
}
