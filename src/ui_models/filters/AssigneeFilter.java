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
public class AssigneeFilter implements AbstractFilter {
    private Collection<String> assigneesToRemove;

    @Override
    public void extractRelevantInformationFromParams(QueryParamsMap params) {
        assigneesToRemove = new HashSet<>(SimilarPatentServer.preProcess(SimilarPatentServer.extractString(params, Constants.ASSIGNEES_TO_REMOVE_FILTER, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]"));

    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return !assigneesToRemove.contains(item.getData(Constants.ASSIGNEE));
    }
}
