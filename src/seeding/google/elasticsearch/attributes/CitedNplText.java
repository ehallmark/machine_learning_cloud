package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CitedNplText extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.CITED_NPL_TEXT;
    }
}
