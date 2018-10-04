package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class Counterparts extends NestedAttribute {
    public Counterparts() {
        super(Arrays.asList(new CounterpartPublicationNumberFull(), new CounterpartPublicationNumber(), new CounterpartPublicationNumberWithCountry(), new CounterpartCountryCode(), new CounterpartKindCode(), new CounterpartApplicationNumberFormatted(), new CounterpartApplicationNumberFormattedWithCountry()),true);
    }

        @Override
    public String getName() {
        return Attributes.COUNTERPARTS;
    }
}
