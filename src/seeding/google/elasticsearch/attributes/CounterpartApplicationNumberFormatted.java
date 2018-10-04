package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CounterpartApplicationNumberFormatted extends AssetKeywordAttribute {
    @Override
    public String getAssetPrefix() {
        return Attributes.COUNTERPARTS+".counterpart_";
    }

    @Override
    public boolean isApplication() {
        return true;
    }

    @Override
    public String getName() {
        return Attributes.COUNTERPART_APPLICATION_NUMBER_FORMATTED;
    }
}
