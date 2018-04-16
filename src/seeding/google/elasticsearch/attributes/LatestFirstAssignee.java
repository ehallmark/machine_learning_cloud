package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestFirstAssignee extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.LATEST_FIRST_ASSIGNEE;
    }
}
