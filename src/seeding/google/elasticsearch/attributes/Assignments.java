package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class Assignments extends NestedAttribute {
    public Assignments() {
        super(Arrays.asList(new ReelFrame(), new ConveyanceText(), new ExecutionDate(), new RecordedDate(), new RecordedAssignee(), new RecordedAssignor()),true);
    }

        @Override
    public String getName() {
        return Attributes.ASSIGNMENTS;
    }
}
