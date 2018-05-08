package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestFamAssigneeCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.LATEST_FAM_ASSIGNEE_COUNT;
    }
}
