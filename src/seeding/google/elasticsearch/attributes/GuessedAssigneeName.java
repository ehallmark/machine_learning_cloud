package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class GuessedAssigneeName extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.GUESSED_ASSIGNEE;
    }

}
