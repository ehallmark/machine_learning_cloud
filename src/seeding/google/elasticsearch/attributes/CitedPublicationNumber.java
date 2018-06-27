package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CitedPublicationNumber extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.CITED_PUBLICATION_NUMBER;
    }

    @Override
    public String getAssetPrefix() {
        return Attributes.CITATIONS+".cited_";
    }

    @Override
    public boolean isApplication() {
        return false;
    }

}
