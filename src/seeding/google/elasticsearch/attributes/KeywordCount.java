package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class KeywordCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.KEYWORD_COUNT;
    }
}
