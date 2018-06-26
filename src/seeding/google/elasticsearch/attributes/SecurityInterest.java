package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class SecurityInterest extends NestedAttribute {
    public SecurityInterest() {
        super(Arrays.asList(new SecurityInterestHolder(), new SecurityInterestDate()),true);
        this.isObject=true;
    }

        @Override
    public String getName() {
        return Attributes.SECURITY_INTEREST;
    }
}
