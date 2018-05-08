package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class InventorCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.INVENTORS_COUNT;
    }
}
