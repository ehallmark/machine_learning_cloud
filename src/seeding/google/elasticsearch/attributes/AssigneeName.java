package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class AssigneeName extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.ASSIGNEE_HARMONIZED;
    }

}
