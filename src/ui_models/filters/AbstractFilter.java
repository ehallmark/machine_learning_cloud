package ui_models.filters;

import lombok.NonNull;
import ui_models.portfolios.AbstractPortfolio;

/**
 * Created by Evan on 5/9/2017.
 */
public interface AbstractFilter {
    void filter(@NonNull AbstractPortfolio portfolio);
}
