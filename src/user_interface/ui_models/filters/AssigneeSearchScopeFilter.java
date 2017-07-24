package user_interface.ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;
import static j2html.TagCreator.*;
import static user_interface.server.SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD;
import static user_interface.server.SimilarPatentServer.extractString;
import static user_interface.server.SimilarPatentServer.preProcess;

/**
 * Created by ehallmark on 5/10/17.
 */
public class AssigneeSearchScopeFilter extends AbstractIncludeFilter {

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 assignee per line").withName(SimilarPatentServer.ASSIGNEES_TO_SEARCH_IN_FIELD)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        labels = preProcess(extractString(req, ASSIGNEES_TO_SEARCH_FOR_FIELD, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]");
    }


    @Override
    public String getName() {
        return Constants.ASSIGNEE_SEARCH_SCOPE_FILTER;
    }

    @Override
    public String getPrerequisite() {
        return Constants.ASSIGNEE;
    }
}
