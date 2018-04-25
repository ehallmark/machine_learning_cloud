package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class ApplicationNumber extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.APPLICATION_NUMBER;
    }
}
