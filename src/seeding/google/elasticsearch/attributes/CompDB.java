package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class CompDB extends NestedAttribute {
    public CompDB() {
        super(Arrays.asList(new CompDBDealID(), new CompDBRecordedDate(), new CompDBTechnology(), new CompDBInactive(), new CompDBAcquisition()),true);
    }

        @Override
    public String getName() {
        return Attributes.COMPDB;
    }
}
