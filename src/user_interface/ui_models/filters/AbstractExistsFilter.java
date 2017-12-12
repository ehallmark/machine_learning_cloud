package user_interface.ui_models.filters;

import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.List;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractExistsFilter extends AbstractBooleanIncludeFilter {
    protected List<String> filters;
    public AbstractExistsFilter(@NonNull AbstractAttribute attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractExistsFilter(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        QueryBuilder query;
        if (isScriptFilter) {
            query = getScriptFilter();
        } else {
            query = QueryBuilders.existsQuery(getFullPrerequisite());
        }
        return query;
    }

    @Override
    protected String transformAttributeScript(String script) {
        return "("+script+") != null";
    }

}
