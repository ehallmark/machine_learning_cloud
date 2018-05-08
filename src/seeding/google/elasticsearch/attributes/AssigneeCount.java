package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class AssigneeCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.ASSIGNEES_COUNT;
    }
}
