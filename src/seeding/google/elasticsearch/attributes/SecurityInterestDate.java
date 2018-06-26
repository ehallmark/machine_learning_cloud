package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class SecurityInterestDate extends DateAttribute {
    @Override
    public String getName() {
        return Attributes.SECURITY_INTEREST_DATE;
    }
}
