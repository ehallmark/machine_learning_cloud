package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import spark.QueryParamsMap;
import ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;

/**
 * Created by ehallmark on 5/10/17.
 */
public class ValueThresholdFilter implements AbstractFilter {
    private Double threshold;

    @Override
    public Tag getOptionsTag() {
        return div().with(
                input().withType("number").attr("step","0.1").withName(Constants.VALUE_THRESHOLD_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(QueryParamsMap params) {
        threshold = Double.valueOf(params.value(Constants.VALUE_THRESHOLD_FILTER));
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return threshold==null ? true : item.getValue()>threshold;
    }
}
