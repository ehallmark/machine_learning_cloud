package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CitedCategory extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.CITED_CATEGORY;
    }
}
