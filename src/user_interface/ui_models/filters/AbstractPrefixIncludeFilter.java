package user_interface.ui_models.filters;

import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractPrefixIncludeFilter extends AbstractIncludeFilter {
    public AbstractPrefixIncludeFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, List<String> labels) {
        super(attribute, filterType, fieldType, labels);
    }


    @Override
    public AbstractFilter dup() {
        return new AbstractPrefixIncludeFilter(attribute,filterType,fieldType,labels==null?null:new ArrayList<>(labels));
    }

    @Override
    public QueryBuilder getFilterQuery() {
        BoolQueryBuilder query = QueryBuilders.boolQuery().minimumShouldMatch(minimumShouldMatch);
        for(String label : labels) {
            query = query.should(QueryBuilders.prefixQuery(getFullPrerequisite(), label));
        }
        return query;
    }

}
