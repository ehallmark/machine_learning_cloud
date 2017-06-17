package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import spark.QueryParamsMap;
import spark.Request;
import ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;
import static j2html.TagCreator.textarea;

/**
 * Created by ehallmark on 5/10/17.
 */
public class PortfolioSizeMaximumFilter extends AbstractFilter {
    private int limit = 0;

    @Override
    public Tag getOptionsTag() {
        return div().with(
                input().withType("number").withName(Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        this.limit = Integer.valueOf(req.queryParams(Constants.PORTFOLIO_SIZE_MAXIMUM_FILTER));
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        if(limit <= 0) return true;

        try {
            return Integer.valueOf(obj.getData(Constants.PORTFOLIO_SIZE).toString()) <= limit;
        } catch(Exception e) {
            return true;
        }
    }
}
