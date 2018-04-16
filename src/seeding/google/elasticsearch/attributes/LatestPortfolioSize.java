package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

public class LatestPortfolioSize extends IntegerAttribute {
    @Override
    public String getName() {
        return Attributes.LATEST_PORTFOLIO_SIZE;
    }
}
