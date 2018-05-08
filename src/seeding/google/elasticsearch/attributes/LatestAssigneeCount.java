package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestAssigneeCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.LATEST_ASSIGNEE_COUNT;
    }
}
