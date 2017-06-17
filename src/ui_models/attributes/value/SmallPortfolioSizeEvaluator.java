package ui_models.attributes.value;

import seeding.Constants;
import ui_models.portfolios.PortfolioList;

import java.util.Collection;

/**
 * Created by Evan on 2/26/2017.
 */
public class SmallPortfolioSizeEvaluator extends PortfolioSizeEvaluator {

    public SmallPortfolioSizeEvaluator() {
        super(Constants.SMALL_PORTFOLIO_VALUE);
    }

    public double evaluate(String token) {
        if(model.containsKey(token)) {
            return ValueMapNormalizer.DEFAULT_END - model.get(token);
        } else {
            return defaultVal;
        }
    }
}
