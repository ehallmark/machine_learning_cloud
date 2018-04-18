package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class RecordedAssignee extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.RECORDED_ASSIGNEE;
    }
}
