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
import java.util.function.Function;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractBooleanExcludeFilter extends AbstractBooleanIncludeFilter {
    public AbstractBooleanExcludeFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractBooleanExcludeFilter(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        QueryBuilder scope = attribute.getQueryScope();
        QueryBuilder query;
        if (isScriptFilter) {
            query = getScriptFilter();
        } else {
            query = QueryBuilders.termQuery(getFullPrerequisite(), false);
        }
        if(scope!=null) {
            query = QueryBuilders.boolQuery()
                    .must(scope)
                    .must(query);
        }
        return query;
    }

    @Override
    protected String transformAttributeScript(String script) {
        return "("+script+") < 0.5";
    }

}
