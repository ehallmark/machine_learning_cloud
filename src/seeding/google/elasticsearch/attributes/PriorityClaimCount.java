package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PriorityClaimCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.PRIORITY_CLAIMS_COUNT;
    }
}
