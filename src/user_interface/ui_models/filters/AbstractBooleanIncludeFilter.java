package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.util.List;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractBooleanIncludeFilter extends AbstractFilter {
    private List<String> filters;
    public AbstractBooleanIncludeFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        return QueryBuilders.termQuery(getFullPrerequisite(), true);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        // do nothing
        filters = SimilarPatentServer.extractArray(params, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    public boolean isActive() {
        return filters != null && filters.contains(getName());
    }
}
