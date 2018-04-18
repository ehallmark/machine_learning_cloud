package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class ExecutionDate extends DateAttribute {

    @Override
    public String getName() {
        return Attributes.EXECUTION_DATE;
    }

}
