package user_interface.ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.HashSet;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;

/**
 * Created by ehallmark on 5/10/17.
 */
public class LabelFilter extends AbstractFilter {
    private Collection<String> labelsToRemove;

    public LabelFilter() {super();};

    public LabelFilter(Collection<String> labelsToRemove) {
        super();
        this.labelsToRemove=labelsToRemove;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 patent per line (eg. 800000)").withName(Constants.LABEL_FILTER)
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

    @Override
    public String getName() {
        return Constants.LABEL_FILTER;
    }

    public boolean isActive() { return labelsToRemove.size()>0; }

}
