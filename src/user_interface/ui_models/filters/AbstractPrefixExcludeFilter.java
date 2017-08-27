package user_interface.ui_models.filters;

import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.List;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractPrefixExcludeFilter extends AbstractExcludeFilter {
    public AbstractPrefixExcludeFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, List<String> labels) {
        super(attribute, filterType, fieldType, labels);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        BoolQueryBuilder query = QueryBuilders.boolQuery();
        for(String label : labels) {
            query = query.mustNot(QueryBuilders.prefixQuery(getFullPrerequisite(), label));
        }
        return query;
    }
    
}
