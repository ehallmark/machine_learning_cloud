package user_interface.ui_models.filters;

import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.List;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractBooleanExcludeFilter extends AbstractBooleanIncludeFilter {
    public AbstractBooleanExcludeFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public List<String> getInputIds() {
        return null;
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
    protected String transformAttributeScript(String script) {
        return "("+script+") < 0.5";
    }

}
