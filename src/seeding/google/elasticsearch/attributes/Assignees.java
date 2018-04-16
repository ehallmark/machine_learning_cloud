package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class Assignees extends NestedAttribute {
    public Assignees() {
        super(Arrays.asList(new AssigneeName(), new AssigneeHarmonizedCC()),true);
    }

        @Override
    public String getName() {
        return Attributes.ASSIGNEES;
    }
}
