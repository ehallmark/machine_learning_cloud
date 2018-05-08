package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class ClaimCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.CLAIMS_COUNT;
    }
}
