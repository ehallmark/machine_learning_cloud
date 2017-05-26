package ui_models.portfolios;

import java.util.Collection;

/**
 * Created by Evan on 5/9/2017.
 */
public interface AbstractPortfolio {
    Collection<String> getTokens();
    AbstractPortfolio merge(AbstractPortfolio other, int limit);
}
