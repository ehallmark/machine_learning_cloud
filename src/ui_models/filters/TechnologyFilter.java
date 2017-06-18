package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static j2html.TagCreator.br;
import static j2html.TagCreator.div;
import static j2html.TagCreator.label;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyFilter extends AbstractFilter {
    private List<String> technologies;
    @Override
    public void extractRelevantInformationFromParams(Request params) {
        technologies = SimilarPatentServer.extractArray(params, SimilarPatentServer.TECHNOLOGIES_TO_FILTER_ARRAY_FIELD);
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(label("Gather Technology"),br(), SimilarPatentServer.gatherTechnologySelect(SimilarPatentServer.TECHNOLOGIES_TO_FILTER_ARRAY_FIELD));
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return technologies.contains(item.getData(Constants.TECHNOLOGY.toString()));
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.TECHNOLOGY);
    }
}
