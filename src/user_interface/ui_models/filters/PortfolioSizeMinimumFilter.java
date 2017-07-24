package user_interface.ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;

/**
 * Created by ehallmark on 5/10/17.
 */
public class PortfolioSizeMinimumFilter extends AbstractGreaterThanFilter {

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
    public String getName() {
        return Constants.PORTFOLIO_SIZE_MINIMUM_FILTER;
    }


    @Override
    public String getPrerequisite() {
        return Constants.PORTFOLIO_SIZE;
    }
}
