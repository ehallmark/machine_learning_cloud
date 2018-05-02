package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class GttKeywords extends KeywordAndTextAttribute implements SignificantTermsAttribute {
    @Override
    public String getName() {
        return Attributes.KEYWORDS;
    }
}
