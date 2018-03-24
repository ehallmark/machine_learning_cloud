package seeding.google.attributes;

import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class Citation extends NestedAttribute {
    public Citation() {
        super(Arrays.asList(new PublicationNumber(), new PublicationNumberFull(), new PublicationNumberWithCountry(),
                new ApplicationNumber(), new ApplicationNumberFull(), new ApplicationNumberWithCountry(), new FilingDate(), new Category(), new Type(), new NplText()));

    }

    @Override
    public String getName() {
        return Constants.CITATION;
    }

}
