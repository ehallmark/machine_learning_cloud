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
public class RemainingLifeFilter extends AbstractFilter {
    private int limit = 0;

    @Override
    public Tag getOptionsTag() {
        return div().with(
                input().withClass("form-control").withType("number").withValue("0").withName(Constants.REMAINING_LIFE)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        this.limit = Integer.valueOf(req.queryParams(Constants.REMAINING_LIFE));
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        if(limit <= 0) return true;

        try {
            return Integer.valueOf(obj.getData(Constants.REMAINING_LIFE).toString()) >= limit;
        } catch(Exception e) {
            return true;
        }
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.REMAINING_LIFE);
    }

    @Override
    public String getName() {
        return Constants.REMAINING_LIFE;
    }
}
