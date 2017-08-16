package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.util.List;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractBooleanExcludeFilter extends AbstractFilter {
    private List<String> filters;
    public AbstractBooleanExcludeFilter(@NonNull AbstractAttribute<?> attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        return QueryBuilders.termQuery(getFullPrerequisite(), false);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
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
