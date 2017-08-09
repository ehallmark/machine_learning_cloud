package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractNestedFilter<T> extends AbstractFilter<T> {
    protected Collection<AbstractFilter> filters;
    public AbstractNestedFilter(@NonNull NestedAttribute nestedAttribute, FilterType filterType) {
        super(nestedAttribute,filterType);
        Collection<AbstractAttribute> attributes = nestedAttribute.getAttributes();
        this.filters = attributes.stream().flatMap(attr->{
            Collection<AbstractFilter> filters = attr.createFilters();
            return filters.stream();
        }).collect(Collectors.toList());
    }

    @Override
    public QueryBuilder getFilterQuery() {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        for(AbstractFilter filter : filters) {
            if(filter.isActive()) {
                boolQuery = boolQuery.must(filter.getFilterQuery());
            }
        }
        return QueryBuilders.nestedQuery(getName(), boolQuery, ScoreMode.Max);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        filters.forEach(filter->{
            if(filter.isActive()) {
                filter.extractRelevantInformationFromParams(params);
            }
        });
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                filters.stream().map(filter->filter.getOptionsTag()).collect(Collectors.toList())
        );
    }

    public boolean isActive() { return filters.stream().anyMatch(filter->filter.isActive()); }
}
