package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestFamAssigneeDate extends DateAttribute {

    @Override
    public String getName() {
        return Attributes.LATEST_FAM_ASSIGNEE_DATE;
    }

}
