package seeding.google.attributes;

import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class InventorHarmonized extends NestedAttribute {
    public InventorHarmonized() {
        super(Arrays.asList(new Name(), new CountryCode()));
    }

    @Override
    public String getName() {
        return Constants.INVENTOR_HARMONIZED;
    }

}
