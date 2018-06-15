package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class PCApplicationNumberFormatted extends AssetKeywordAttribute {
    @Override
    public String getAssetPrefix() {
        return Attributes.PRIORITY_CLAIMS+".pc_";
    }

    @Override
    public boolean isApplication() {
        return true;
    }

    @Override
    public String getName() {
        return Attributes.PC_APPLICATION_NUMBER_FORMATTED;
    }
}
