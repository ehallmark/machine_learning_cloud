package seeding.google.attributes;

import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class FI extends NestedAttribute {
    public FI() {
        super(Arrays.asList(new Code(), new Inventive(), new First(), new Tree()));
    }

    @Override
    public String getName() {
        return Constants.FI;
    }

}
