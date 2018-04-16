package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PublicationDate extends DateAttribute {
    @Override
    public String getName() {
        return Attributes.PUBLICATION_DATE;
    }
}
