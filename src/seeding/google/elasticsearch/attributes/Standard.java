package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class Standard extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.STANDARD;
    }
}