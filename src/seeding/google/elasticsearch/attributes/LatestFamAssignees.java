package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class LatestFamAssignees extends NestedAttribute {
    public LatestFamAssignees() {
        super(Arrays.asList(new InventorName()),true);
    }

        @Override
    public String getName() {
        return Attributes.LATEST_FAM_ASSIGNEES;
    }
}
