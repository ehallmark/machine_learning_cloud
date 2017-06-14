package ui_models.filters;

import seeding.Constants;
import seeding.Database;
import spark.QueryParamsMap;
import ui_models.portfolios.items.Item;

/**
 * Created by ehallmark on 5/10/17.
 */
public class PortfolioSizeMaximumFilter implements AbstractFilter {
    private int limit = 0;

    @Override
    public void extractRelevantInformationFromParams(QueryParamsMap params) {
        this.limit = Integer.valueOf(params.value(Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER));
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        if(limit <= 0) return true;

        String assignee = obj.getData(Constants.ASSIGNEE).toString();
        if(assignee!=null) {
            int portfolioSize = Database.getAssetCountFor(assignee);
            return portfolioSize <= limit;
        } else return true;
    }
}
