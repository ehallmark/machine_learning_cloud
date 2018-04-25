package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PublicationNumberWithCountry extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.PUBLICATION_NUMBER_WITH_COUNTRY;
    }
}
