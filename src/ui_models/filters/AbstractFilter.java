package ui_models.filters;

import lombok.NonNull;
import ui_models.portfolios.AbstractPortfolio;
import ui_models.portfolios.items.Item;

/**
 * Created by Evan on 5/9/2017.
 */
public interface AbstractFilter {
    enum Type {
        PreFilter, PostFilter
    }
    boolean shouldKeepItem(@NonNull Item item);
}
