package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class Standards extends NestedAttribute {
    public Standards() {
        super(Arrays.asList(new Standard(), new SSO()),true);
    }

        @Override
    public String getName() {
        return Attributes.STANDARDS;
    }
}
