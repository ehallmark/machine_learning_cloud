package seeding.google.attributes;

import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class Examiner extends NestedAttribute {
    public Examiner() {
        super(Arrays.asList(new Name(), new Department(), new Level()));
    }

    @Override
    public String getName() {
        return Constants.EXAMINER;
    }

}
