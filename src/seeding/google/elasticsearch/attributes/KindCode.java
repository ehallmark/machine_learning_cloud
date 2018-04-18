package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class KindCode extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.KIND_CODE;
    }
}
