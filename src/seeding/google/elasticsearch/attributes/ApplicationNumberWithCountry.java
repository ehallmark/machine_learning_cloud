package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class ApplicationNumberWithCountry extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.APPLICATION_NUMBER_WITH_COUNTRY;
    }
}
