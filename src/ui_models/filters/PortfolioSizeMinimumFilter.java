package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import spark.QueryParamsMap;
import ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;

/**
 * Created by ehallmark on 5/10/17.
 */
public class PortfolioSizeMinimumFilter extends AbstractFilter {
    private int limit = 0;

    @Override
    public Tag getOptionsTag() {
        return div().with(
                input().withType("number").withName(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(QueryParamsMap params) {
        this.limit = Integer.valueOf(params.value(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER));
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        if(limit <= 0) return true;

        String assignee = obj.getData(Constants.ASSIGNEE).toString();
        if(assignee!=null) {
            int portfolioSize = Database.getAssetCountFor(assignee);
            return portfolioSize >= limit;
        } else return true;
    }
}
