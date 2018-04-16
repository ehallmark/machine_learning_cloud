package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class RCitations extends NestedAttribute {
    public RCitations() {
        super(Arrays.asList(new InventorName()),true);
    }

        @Override
    public String getName() {
        return Attributes.RCITATIONS;
    }
}
