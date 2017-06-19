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
    private Set<String> wipoTechnologies;
    @Override
    public void extractRelevantInformationFromParams(Request params) {
        technologies = new HashSet<>(SimilarPatentServer.extractArray(params, SimilarPatentServer.TECHNOLOGIES_TO_FILTER_ARRAY_FIELD));
        wipoTechnologies = new HashSet<>(SimilarPatentServer.extractArray(params, SimilarPatentServer.WIPO_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD));
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                label("Gather Technology"),br(),
                SimilarPatentServer.gatherTechnologySelect(SimilarPatentServer.TECHNOLOGIES_TO_FILTER_ARRAY_FIELD),
                label("WIPO Technology"),br(),
                SimilarPatentServer.technologySelect(SimilarPatentServer.WIPO_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD, WIPOHelper.getOrderedClassifications())
        );
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return (technologies.isEmpty()||technologies.contains(item.getData(Constants.TECHNOLOGY.toString())))
                && (wipoTechnologies.isEmpty() || wipoTechnologies.contains(item.getData(Constants.WIPO_TECHNOLOGY).toString()));
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.TECHNOLOGY,Constants.WIPO_TECHNOLOGY);
    }
}
