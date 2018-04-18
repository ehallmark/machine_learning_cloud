package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class Citations extends NestedAttribute {
    public Citations() {
        super(Arrays.asList(new CitedFilingDate()),true);
    }

        @Override
    public String getName() {
        return Attributes.CITATIONS;
    }
}
