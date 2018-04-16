package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CountryCode extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.COUNTRY_CODE;
    }
}
