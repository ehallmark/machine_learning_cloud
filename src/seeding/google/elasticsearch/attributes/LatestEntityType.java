package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestEntityType extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.LATEST_ENTITY_TYPE;
    }
}
