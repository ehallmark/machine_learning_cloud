package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class Code extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.CODE;
    }
}
