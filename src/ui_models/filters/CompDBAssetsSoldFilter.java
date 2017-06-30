package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;

/**
 * Created by ehallmark on 5/10/17.
 */
public class CompDBAssetsSoldFilter extends AbstractFilter {
    private int limit = 0;

    @Override
    public Tag getOptionsTag() {
        return div().with(
                input().withClass("form-control").withType("number").withValue("0").withName(Constants.COMPDB_ASSETS_SOLD)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        this.limit = Integer.valueOf(req.queryParams(Constants.COMPDB_ASSETS_SOLD));
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        if(limit <= 0) return true;

        try {
            return Integer.valueOf(obj.getData(Constants.COMPDB_ASSETS_SOLD).toString()) >= limit;
        } catch(Exception e) {
            return false;
        }
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.COMPDB_ASSETS_SOLD);
    }

    @Override
    public String getName() {
        return Constants.COMPDB_ASSETS_SOLD;
    }

    public boolean isActive() { return limit > 0; }

}
