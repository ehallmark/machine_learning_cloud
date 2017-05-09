package ui_models.attributes.value;

import ui_models.portfolios.AbstractPortfolio;

import java.util.Collection;

/**
 * Created by Evan on 2/26/2017.
 */
public class SmallPortfolioSizeEvaluator extends PortfolioSizeEvaluator {

    public SmallPortfolioSizeEvaluator() {
        super("Small Portfolio Size Value");
    }

    // Returns value between 1 and 5
    @Override
    public Double attributesFor(AbstractPortfolio portfolio, int n) {
        Collection<String> tokens = portfolio.getTokens();
        // flip the value
        if(tokens.size()>0 && tokens.stream().anyMatch(token->model.containsKey(token))) {
            double val = super.attributesFor(portfolio,n);
            double distFromStart = val - ValueMapNormalizer.DEFAULT_START;
            val = ValueMapNormalizer.DEFAULT_END-distFromStart;
            return val;
        } else {
            return ValueMapNormalizer.DEFAULT_START;
        }
    }
}
