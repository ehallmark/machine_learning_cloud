package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class SecurityInterestFam extends NestedAttribute {
    public SecurityInterestFam() {
        super(Arrays.asList(new SecurityInterestFamHolder(), new SecurityInterestFamDate()),true);
        this.isObject=true;
    }

        @Override
    public String getName() {
        return Attributes.SECURITY_INTEREST_FAM;
    }
}
