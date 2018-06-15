package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class CitedApplicationNumberFormatted extends AssetKeywordAttribute {
    @Override
    public String getAssetPrefix() {
        return Attributes.CITATIONS+".cited_";
    }

    @Override
    public boolean isApplication() {
        return true;
    }

    @Override
    public String getName() {
        return Attributes.CITED_APPLICATION_NUMBER_FORMATTED;
    }
}
