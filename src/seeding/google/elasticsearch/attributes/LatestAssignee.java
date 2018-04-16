package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestAssignee extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.LATEST_ASSIGNEE;
    }
}
