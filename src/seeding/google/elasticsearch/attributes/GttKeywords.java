package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class GttKeywords extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.KEYWORDS;
    }
}
