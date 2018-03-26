package seeding.google.attributes;

import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class DescriptionLocalized extends NestedAttribute {
    public DescriptionLocalized() {
        super(Arrays.asList(new Text(), new Language()));
    }

    @Override
    public String getName() {
        return Constants.DESCRIPTION_LOCALIZED;
    }

}
