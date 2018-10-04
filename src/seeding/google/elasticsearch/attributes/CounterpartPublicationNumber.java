package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collections;

public class CounterpartPublicationNumber extends AssetKeywordAttribute {
    @Override
    public String getAssetPrefix() {
        return Attributes.COUNTERPARTS+".counterpart_";
    }

    @Override
    public String getName() {
        return Attributes.COUNTERPART_PUBLICATION_NUMBER;
    }

    @Override
    public boolean isApplication() {
        return false;
    }


}
