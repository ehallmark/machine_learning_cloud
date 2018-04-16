package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PCFilingDate extends DateAttribute {
    @Override
    public String getName() {
        return Attributes.PC_FILING_DATE;
    }
}
