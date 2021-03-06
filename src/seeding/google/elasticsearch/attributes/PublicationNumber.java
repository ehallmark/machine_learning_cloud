package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PublicationNumber extends AssetKeywordAttribute {

    @Override
    public String getName() {
        return Attributes.PUBLICATION_NUMBER;
    }

    @Override
    public String getAssetPrefix() {
        return "";
    }

    @Override
    public boolean isApplication() {
        return false;
    }
}
