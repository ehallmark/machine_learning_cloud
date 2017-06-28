package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.portfolios.items.Item;

import static j2html.TagCreator.*;

/**
 * Created by ehallmark on 5/10/17.
 */
public class AssigneeSearchScopeFilter extends AbstractFilter {

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 assignee per line").withName(SimilarPatentServer.ASSIGNEES_TO_SEARCH_IN_FIELD)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return true;
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE_SEARCH_SCOPE_FILTER;
    }
}
