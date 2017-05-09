package ui_models.attributes;

import ui_models.portfolios.AbstractPortfolio;

/**
 * Created by Evan on 5/9/2017.
 */
public interface AbstractAttribute<T> {
    T attributesFor(AbstractPortfolio portfolio, int limit);
}
