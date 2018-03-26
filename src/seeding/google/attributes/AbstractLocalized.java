package seeding.google.attributes;

import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class AbstractLocalized extends NestedAttribute {
    public AbstractLocalized() {
        super(Arrays.asList(new Text(), new Language()));
    }

    @Override
    public String getName() {
        return Constants.ABSTRACT_LOCALIZED;
    }

}
