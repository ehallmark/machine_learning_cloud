package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class Citations extends NestedAttribute {
    public Citations() {
        super(Arrays.asList(new CitedFilingDate(), new CitedCategory(), new CitedApplicationNumberFull(), new CitedPublicationNumberFull(), new CitedType(), new CitedNplText()),true);
    }

        @Override
    public String getName() {
        return Attributes.CITATIONS;
    }
}
