package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class ConveyanceText extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.CONVEYANCE_TEXT;
    }
}
