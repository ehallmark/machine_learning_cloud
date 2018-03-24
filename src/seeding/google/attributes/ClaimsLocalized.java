package seeding.google.attributes;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

public class ClaimsLocalized extends NestedAttribute {
    public ClaimsLocalized() {
        super(Arrays.asList(new Text(), new Language()));
    }

    @Override
    public String getName() {
        return Constants.CLAIMS_LOCALIZED;
    }

}
