package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import spark.QueryParamsMap;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;

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
                input().withClass("form-control").withType("number").withValue("0").withName(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        this.limit = Integer.valueOf(req.queryParams(Constants.PORTFOLIO_SIZE_MINIMUM_FILTER));
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        if(limit <= 0) return true;

        try {
            return Integer.valueOf(obj.getData(Constants.PORTFOLIO_SIZE).toString()) >= limit;
        } catch(Exception e) {
            return true;
        }
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.PORTFOLIO_SIZE);
    }

    @Override
    public String getName() {
        return Constants.PORTFOLIO_SIZE_MINIMUM_FILTER;
    }
}
