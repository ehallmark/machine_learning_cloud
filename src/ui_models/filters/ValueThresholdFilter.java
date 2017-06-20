package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
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
public class ValueThresholdFilter extends AbstractFilter {
    private Double threshold;

    @Override
    public Tag getOptionsTag() {
        return div().with(
                input().withType("number").attr("step","0.1").withName(Constants.VALUE_THRESHOLD_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        threshold = Double.valueOf(req.queryParams(Constants.VALUE_THRESHOLD_FILTER));
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return threshold==null ? true : ((Number)item.getData(Constants.AI_VALUE)).doubleValue()>threshold;
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.AI_VALUE);
    }

    @Override
    public String getName() {
        return Constants.VALUE_THRESHOLD_FILTER;
    }
}
