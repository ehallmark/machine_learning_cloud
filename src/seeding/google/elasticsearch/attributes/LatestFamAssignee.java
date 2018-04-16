package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestFamAssignee extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.LATEST_FAM_ASSIGNEE;
    }
}
