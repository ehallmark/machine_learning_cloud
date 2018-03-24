package seeding.google.attributes;

import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class AssigneeHarmonized extends NestedAttribute {
    public AssigneeHarmonized() {
        super(Arrays.asList(new Name(), new CountryCode()));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE_HARMONIZED;
    }

}
