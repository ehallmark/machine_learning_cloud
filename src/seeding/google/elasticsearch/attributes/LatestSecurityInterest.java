package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestSecurityInterest extends BooleanAttribute {
    @Override
    public String getName() {
        return Attributes.LATEST_SECURITY_INTEREST;
    }
}
