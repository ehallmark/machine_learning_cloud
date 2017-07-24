package user_interface.ui_models.filters;

import j2html.tags.Tag;
import models.classification_models.WIPOHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
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
public class AbstractTechnologyFilter extends AbstractIncludeFilter {
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
        labels = new HashSet<>(SimilarPatentServer.extractArray(params, filterName));
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                SimilarPatentServer.technologySelect(filterName, allTechnologies)
        );
    }

    @Override
    public String getPrerequisite() {
        return attrName;
    }

    @Override
    public String getName() {
        return attrName;
    }


}
