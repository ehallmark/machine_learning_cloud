package ui_models.portfolios.attributes;

import seeding.Constants;
import seeding.Database;
import ui_models.attributes.AbstractAttribute;

import java.util.Collection;

/**
 * Created by ehallmark on 6/15/17.
 */
public class PortfolioSizeAttribute implements AbstractAttribute<Integer> {

    @Override
    public Integer attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return 0;
        return Database.getAssetCountFor(portfolio.stream().findAny().get());
    }

    @Override
    public String getName() {
        return Constants.PORTFOLIO_SIZE;
    }
}
