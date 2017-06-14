package ui_models.filters;

import seeding.Database;
import spark.QueryParamsMap;
import ui_models.portfolios.items.Item;

/**
 * Created by Evan on 6/13/2017.
 */
public class ExpirationFilter implements AbstractFilter {
    @Override
    public void extractRelevantInformationFromParams(QueryParamsMap params) {
        // do nothing
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        return !Database.isExpired(obj.getName());
    }
}
