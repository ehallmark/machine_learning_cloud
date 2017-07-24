package user_interface.ui_models.filters;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;

/**
 * Created by Evan on 6/17/2017.
 */
public abstract class AbstractExcludeFilter extends AbstractFilter {
    protected Collection<String> labels;

    @Override
    public QueryBuilder getFilterQuery() {
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
        for(String tech : labels) {
            builder = builder
                    .mustNot(QueryBuilders.termQuery(getPrerequisite(),tech));
        }
        return builder;
    }

    public boolean isActive() { return labels.size() > 0; }

    @Override
    public boolean shouldKeepItem(Item item) {
        return labels==null||labels.isEmpty()||!labels.contains(item.getData(getPrerequisite()));
    }
}
