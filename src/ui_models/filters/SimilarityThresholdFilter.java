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
public class SimilarityThresholdFilter extends AbstractFilter {
    private Double threshold;


    @Override
    public Tag getOptionsTag() {
        return div().with(
                input().withClass("form-control").withType("number").withValue("0").withName(Constants.SIMILARITY_THRESHOLD_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        threshold = Double.valueOf(req.queryParams(Constants.SIMILARITY_THRESHOLD_FILTER));
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return threshold==null ? true : item.getSimilarity()>threshold;
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.SIMILARITY);
    }

    @Override
    public String getName() {
        return Constants.SIMILARITY_THRESHOLD_FILTER;
    }

    public boolean isActive() { return threshold!=null && threshold > 0; }

}
