package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import server.SimilarPatentServer;
import spark.QueryParamsMap;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.HashSet;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;

/**
 * Created by ehallmark on 5/10/17.
 */
public class LabelFilter extends AbstractFilter {
    private Collection<String> labelsToRemove;

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withName(Constants.LABEL_FILTER)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        labelsToRemove = new HashSet<>(SimilarPatentServer.preProcess(SimilarPatentServer.extractString(req, Constants.LABEL_FILTER, "").toUpperCase(), "\n", "[^a-zA-Z0-9 ]"));
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return !labelsToRemove.contains(item.getName());
    }
}
