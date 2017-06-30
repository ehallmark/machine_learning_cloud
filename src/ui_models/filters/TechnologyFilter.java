package ui_models.filters;

import classification_models.WIPOHelper;
import j2html.tags.Tag;
import seeding.Constants;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.*;

import static j2html.TagCreator.br;
import static j2html.TagCreator.div;
import static j2html.TagCreator.label;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyFilter extends AbstractFilter {
    private Set<String> technologies;
    @Override
    public void extractRelevantInformationFromParams(Request params) {
        technologies = new HashSet<>(SimilarPatentServer.extractArray(params, SimilarPatentServer.TECHNOLOGIES_TO_FILTER_ARRAY_FIELD));
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                SimilarPatentServer.gatherTechnologySelect(SimilarPatentServer.TECHNOLOGIES_TO_FILTER_ARRAY_FIELD)
        );
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return technologies.isEmpty()||technologies.contains(item.getData(Constants.TECHNOLOGY.toString()));
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.TECHNOLOGY);
    }

    @Override
    public String getName() {
        return Constants.TECHNOLOGY;
    }

    public boolean isActive() { return technologies.size() > 0; }

}
