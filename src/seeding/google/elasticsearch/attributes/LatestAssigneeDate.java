package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestAssigneeDate extends DateAttribute {

    @Override
    public String getName() {
        return Attributes.LATEST_ASSIGNEE_DATE;
    }

}
