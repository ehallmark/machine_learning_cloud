package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

/**
 * Created by ehallmark on 7/20/17.
 */
public class CalculatedPriorityDate extends DateAttribute {

    @Override
    public String getName() {
        return Attributes.PRIORITY_DATE_ESTIMATED;
    }

}
