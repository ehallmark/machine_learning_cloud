package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PtabInventorLastName extends KeywordAndTextAttribute {
    @Override
    public String getName() {
        return Attributes.PTAB_INVENTOR_LAST_NAME;
    }
}
