package ui_models.filters;

import seeding.Constants;
import spark.QueryParamsMap;
import ui_models.portfolios.items.Item;

/**
 * Created by ehallmark on 5/10/17.
 */
public class ThresholdFilter implements AbstractFilter {
    private Double threshold;

    @Override
    public void extractRelevantInformationFromParams(QueryParamsMap params) {
        threshold = Double.valueOf(params.value(Constants.SIMILARITY_THRESHOLD));
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return threshold==null ? true : item.getSimilarity()>threshold;
    }
}
