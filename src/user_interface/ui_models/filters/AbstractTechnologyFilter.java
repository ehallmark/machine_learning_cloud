package user_interface.ui_models.filters;

import j2html.tags.Tag;
import models.classification_models.WIPOHelper;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractTechnologyFilter extends AbstractFilter {
    private Set<String> technologies;
    private Collection<String> allTechnologies;
    private String attrName;
    private String filterName;
    protected AbstractTechnologyFilter(Collection<String> allTechnologies, String attrName, String filterName) {
        this.allTechnologies=allTechnologies;
        this.attrName=attrName;
        this.filterName=filterName;
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        technologies = new HashSet<>(SimilarPatentServer.extractArray(params, filterName));
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                SimilarPatentServer.technologySelect(filterName, allTechnologies)
        );
    }

    @Override
    public boolean shouldKeepItem(Item item) {
        return technologies==null||technologies.isEmpty()||technologies.contains(item.getData(attrName));
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(attrName);
    }

    @Override
    public String getName() {
        return attrName;
    }

    public boolean isActive() { return technologies.size() > 0; }

}
