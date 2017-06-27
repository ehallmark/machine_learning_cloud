package ui_models.filters;

import classification_models.WIPOHelper;
import j2html.tags.Tag;
import seeding.Constants;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 6/17/2017.
 */
public class WIPOTechnologyFilter extends AbstractFilter {
    private Set<String> wipoTechnologies;
    @Override
    public void extractRelevantInformationFromParams(Request params) {
        wipoTechnologies = new HashSet<>(SimilarPatentServer.extractArray(params, SimilarPatentServer.WIPO_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD));
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                label("WIPO Technology"),br(),
                SimilarPatentServer.technologySelect(SimilarPatentServer.WIPO_TECHNOLOGIES_TO_FILTER_ARRAY_FIELD, WIPOHelper.getOrderedClassifications())
        );
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return  wipoTechnologies.isEmpty() || wipoTechnologies.contains(item.getData(Constants.WIPO_TECHNOLOGY).toString());
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.WIPO_TECHNOLOGY);
    }

    @Override
    public String getName() {
        return Constants.WIPO_TECHNOLOGY;
    }
}
