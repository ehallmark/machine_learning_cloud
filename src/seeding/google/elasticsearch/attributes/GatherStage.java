package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class GatherStage extends KeywordAttribute {
    @Override
    public String getName() {
        return Attributes.GATHER_STAGE;
    }
}
