package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class RciteApplicationNumberFormatted extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.RCITE_APPLICATION_NUMBER_FULL;
    }

    @Override
    public String getAssetPrefix() {
        return null;
    }

    @Override
    public boolean isApplication() {
        return false;
    }
}
