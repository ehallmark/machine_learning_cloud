package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class RCitationCount extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.RCITATIONS_COUNT;
    }
}
