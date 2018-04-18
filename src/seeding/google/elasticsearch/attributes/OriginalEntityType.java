package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class OriginalEntityType extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.ORIGINAL_ENTITY_TYPE;
    }
}
