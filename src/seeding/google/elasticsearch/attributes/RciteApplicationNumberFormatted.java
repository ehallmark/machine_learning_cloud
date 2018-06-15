package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class RciteApplicationNumberFormatted extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.RCITE_APPLICATION_NUMBER_FORMATTED;
    }

    @Override
    public String getAssetPrefix() {
        return Attributes.RCITATIONS+".rcite_";
    }

    @Override
    public boolean isApplication() {
        return true;
    }
}
