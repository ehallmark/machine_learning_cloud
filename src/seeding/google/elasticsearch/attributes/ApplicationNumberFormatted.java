package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class ApplicationNumberFormatted extends AssetKeywordAttribute {
    @Override
    public String getName() {
        return Attributes.APPLICATION_NUMBER_FORMATTED;
    }

    @Override
    public String getAssetPrefix() {
        return "";
    }

    @Override
    public boolean isApplication() {
        return true;
    }
}
