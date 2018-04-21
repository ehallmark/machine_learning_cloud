package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PtabCaseName extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.PTAB_CASE_NAME;
    }
}
