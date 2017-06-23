package ui_models.filters;

import j2html.tags.Tag;
import static j2html.TagCreator.*;
import seeding.Constants;
import server.SimilarPatentServer;
import spark.QueryParamsMap;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * Created by ehallmark on 5/10/17.
 */
public class AssigneeFilter extends AbstractFilter {
    protected Collection<String> assigneesToRemove;

    public AssigneeFilter() {}

    protected AssigneeFilter(Collection<String> assigneesToRemove) {
        this.assigneesToRemove=assigneesToRemove;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").withName(Constants.ASSIGNEES_TO_REMOVE_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        assigneesToRemove = new HashSet<>(SimilarPatentServer.preProcess(SimilarPatentServer.extractString(req, Constants.ASSIGNEES_TO_REMOVE_FILTER, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]"));
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return !assigneesToRemove.contains(item.getData(Constants.ASSIGNEE));
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.ASSIGNEE);
    }


    @Override
    public String getName() {
        return Constants.ASSIGNEES_TO_REMOVE_FILTER;
    }
}
