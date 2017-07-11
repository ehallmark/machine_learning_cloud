package user_interface.ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import static j2html.TagCreator.*;

/**
 * Created by ehallmark on 5/10/17.
 */
public class PatentSearchScopeFilter extends AbstractFilter {

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 patent per line (eg. 800000)").withName(SimilarPatentServer.PATENTS_TO_SEARCH_IN_FIELD)
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
        return Constants.PATENT_SEARCH_SCOPE_FILTER;
    }

    public boolean isActive() { return false; }

}
