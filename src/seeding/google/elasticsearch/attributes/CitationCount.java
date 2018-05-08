package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CitationCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.CITATIONS_COUNT;
    }
}
