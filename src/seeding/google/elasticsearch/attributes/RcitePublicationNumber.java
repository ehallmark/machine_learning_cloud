package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class RcitePublicationNumberFull extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.RCITE_PUBLICATION_NUMBER_FULL;
    }

    @Override
    public String getAssetPrefix() {
        return Attributes.RCITATIONS+".rcite_";
    }

    @Override
    public boolean isApplication() {
        return false;
    }
}
