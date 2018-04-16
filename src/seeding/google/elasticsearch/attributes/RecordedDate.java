package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class RecordedDate extends DateAttribute {

    @Override
    public String getName() {
        return Attributes.RECORDED_DATE;
    }

}
