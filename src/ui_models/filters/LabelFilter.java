package ui_models.filters;

import seeding.Constants;
import server.SimilarPatentServer;
import spark.QueryParamsMap;
import ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.HashSet;

/**
 * Created by ehallmark on 5/10/17.
 */
public class LabelFilter implements AbstractFilter {
    private Collection<String> labelsToRemove;

    @Override
    public void extractRelevantInformationFromParams(QueryParamsMap params) {
        labelsToRemove = new HashSet<>(SimilarPatentServer.preProcess(SimilarPatentServer.extractString(params, Constants.LABEL_FILTER, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]"));
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return !labelsToRemove.contains(item.getName());
    }
}
