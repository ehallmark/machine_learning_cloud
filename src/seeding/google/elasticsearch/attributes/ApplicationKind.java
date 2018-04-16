package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class ApplicationKind extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.APPLICATION_KIND;
    }
}
