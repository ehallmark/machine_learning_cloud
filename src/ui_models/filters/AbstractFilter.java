package ui_models.filters;

import lombok.NonNull;
import spark.QueryParamsMap;
import ui_models.portfolios.items.Item;

/**
 * Created by Evan on 5/9/2017.
 */
public interface AbstractFilter {
    enum Type {
        PreFilter, PostFilter
    }
    void extractRelevantInformationFromParams(QueryParamsMap params);
    boolean shouldKeepItem(@NonNull Item item);
}
