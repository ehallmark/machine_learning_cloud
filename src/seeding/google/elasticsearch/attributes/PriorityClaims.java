package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class PriorityClaims extends NestedAttribute {
    public PriorityClaims() {
        super(Arrays.asList(new PCApplicationNumberFull(), new PCFilingDate(), new PCPublicationNumberFull()),true);
    }

        @Override
    public String getName() {
        return Attributes.PRIORITY_CLAIMS;
    }
}
