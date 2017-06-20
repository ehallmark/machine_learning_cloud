package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.HashSet;

import static j2html.TagCreator.*;
import static j2html.TagCreator.br;
import static j2html.TagCreator.label;

/**
 * Created by ehallmark on 5/10/17.
 */
public class SearchScopeFilter extends AbstractFilter {

    @Override
    public Tag getOptionsTag() {
        return div().with(
                h5("Search Within"),
                label("Custom Patent List (1 per line)"),br(),
                textarea().withName(SimilarPatentServer.PATENTS_TO_SEARCH_IN_FIELD),br(),
                label("Custom Assignee List (1 per line)"),br(),
                textarea().withName(SimilarPatentServer.ASSIGNEES_TO_SEARCH_IN_FIELD)
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
        return Constants.SEARCH_SCOPE_FILTER;
    }
}
