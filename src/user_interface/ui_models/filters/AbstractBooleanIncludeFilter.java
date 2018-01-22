package user_interface.ui_models.filters;

import j2html.tags.Tag;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.List;
import java.util.function.Function;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractBooleanIncludeFilter extends AbstractFilter {
    protected List<String> filters;
    public AbstractBooleanIncludeFilter(AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public List<String> getInputIds() {
        return null;
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractBooleanIncludeFilter(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        QueryBuilder scope = attribute.getQueryScope();
        QueryBuilder query;
        if (isScriptFilter) {
            query = getScriptFilter();
        } else {
            query = QueryBuilders.termQuery(getFullPrerequisite(), true);
        }
        if(scope!=null) {
            // should return true if (in the scope and in the query) or if not in the scope
            //   this equates to (should be in the complement of scope OR should be in the query)
            QueryBuilder complementOfScope = QueryBuilders.boolQuery().mustNot(scope);
            query = QueryBuilders.boolQuery()
                    .should(complementOfScope)
                    .should(query);
        }
        return query;
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        // do nothing
        filters = SimilarPatentServer.extractArray(params, SimilarPatentServer.PRE_FILTER_ARRAY_FIELD);
        AbstractFilter parent = getParent();
        while(parent!=null && filters.contains(parent.getName())) {
            filters = SimilarPatentServer.extractArray(params, parent.getName());
            parent = parent.getParent();
        }
    }

    @Override
    protected String transformAttributeScript(String script) {
        return "("+script+") > 0.5";
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div();
    }

    public boolean isActive() {
        return filters != null && filters.contains(getName());
    }
}
