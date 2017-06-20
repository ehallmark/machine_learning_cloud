package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import spark.QueryParamsMap;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.Collection;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;

/**
 * Created by Evan on 6/13/2017.
 */
public class ExpirationFilter extends AbstractFilter {
    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        // do nothing
    }

    @Override
    public boolean defaultSelected() {
        return true;
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        return !Database.isExpired(obj.getName());
    }


    @Override
    public String getName() {
        return Constants.EXPIRATION_FILTER;
    }

}
