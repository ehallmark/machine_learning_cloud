package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class Citations extends NestedAttribute {
    public Citations() {
        super(Arrays.asList(new CitedPublicationNumberFull(), new CitedPublicationNumber(), new CitedPublicationNumberWIthCountry(), new CitedApplicationNumberFormatted(), new CitedApplicationNumberFormattedWithCountry(), new CitedFamilyId(), new CitedFilingDate(), new CitedCategory(), new CitedType(), new CitedNplText()),true);
    }

        @Override
    public String getName() {
        return Attributes.CITATIONS;
    }
}
