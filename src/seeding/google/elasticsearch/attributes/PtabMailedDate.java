package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PtabMailedDate extends DateAttribute {
    @Override
    public String getName() {
        return Attributes.PTAB_MAILED_DATE;
    }
}
