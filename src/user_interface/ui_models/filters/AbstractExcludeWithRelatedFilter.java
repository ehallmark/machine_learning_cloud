package user_interface.ui_models.filters;

import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractExcludeWithRelatedFilter extends AbstractIncludeWithRelatedFilter {
    public AbstractExcludeWithRelatedFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, Collection<String> labels) {
        super(attribute, filterType, fieldType, labels==null?null:new ArrayList<>(labels));

    }

    @Override
    public AbstractFilter dup() {
        return new AbstractExcludeWithRelatedFilter(attribute,filterType,fieldType, labels==null?null:new ArrayList<>(labels));
    }

    @Override
    public QueryBuilder getFilterQuery() {
        QueryBuilder builder = super.getFilterQuery();
        if(builder==null) return null;
        return QueryBuilders.boolQuery().mustNot(builder);
    }
}
