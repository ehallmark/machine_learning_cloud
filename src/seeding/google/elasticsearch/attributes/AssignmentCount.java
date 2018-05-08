package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class AssignmentCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.ASSIGNMENTS_COUNT;
    }
}
