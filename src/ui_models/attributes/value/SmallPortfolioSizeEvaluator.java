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

    // Returns value between 1 and 5
    @Override
    public Double attributesFor(Collection<String> tokens, int n) {
        // flip the value
        if(tokens.size()>0 && tokens.stream().anyMatch(token->model.containsKey(token))) {
            double val = super.attributesFor(tokens,n);
            double distFromStart = val - ValueMapNormalizer.DEFAULT_START;
            val = ValueMapNormalizer.DEFAULT_END-distFromStart;
            return val;
        } else {
            return ValueMapNormalizer.DEFAULT_START;
        }
    }
}
