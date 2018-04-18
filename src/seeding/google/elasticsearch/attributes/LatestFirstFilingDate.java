package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestFirstFilingDate extends DateAttribute {

    @Override
    public String getName() {
        return Attributes.LATEST_FIRST_FILING_DATE;
    }

}
