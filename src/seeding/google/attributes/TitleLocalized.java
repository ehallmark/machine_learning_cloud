package seeding.google.attributes;

import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class TitleLocalized extends NestedAttribute {
    public TitleLocalized() {
        super(Arrays.asList(new Text(), new Language()));
    }

    @Override
    public String getName() {
        return Constants.TITLE_LOCALIZED;
    }

}
