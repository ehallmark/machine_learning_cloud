package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PCPublicationNumberFull extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.PC_PUBLICATION_NUMBER;
    }

    @Override
    public String getAssetPrefix() {
        return Attributes.PRIORITY_CLAIMS+".pc_";
    }

    @Override
    public boolean isApplication() {
        return false;
    }
}
