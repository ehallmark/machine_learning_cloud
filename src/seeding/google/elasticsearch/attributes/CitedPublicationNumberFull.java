package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CitedPublicationNumberFull extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.CITED_PUBLICATION_NUMBER_FULL;
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
