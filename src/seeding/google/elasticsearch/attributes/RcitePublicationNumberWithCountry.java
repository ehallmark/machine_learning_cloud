package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class RcitePublicationNumber extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.RCITE_PUBLICATION_NUMBER;
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
