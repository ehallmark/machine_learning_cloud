package user_interface.ui_models.filters;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import user_interface.ui_models.portfolios.items.Item;

/**
 * Created by Evan on 6/17/2017.
 */
public abstract class AbstractLessThanFilter extends AbstractFilter {
    protected Number limit;

    @Override
    public QueryBuilder getFilterQuery() {
        if(limit == null || limit.doubleValue() <= 0d) {
            return QueryBuilders.boolQuery();
        } else {
            return QueryBuilders.rangeQuery(getPrerequisite())
                    .lt(limit);
        }
    }

    public boolean isActive() { return limit.doubleValue() > 0d; }

    @Override
    public boolean shouldKeepItem(Item obj) {
        if(limit.doubleValue() <= 0d) return true;

        try {
            return Double.valueOf(obj.getData(getPrerequisite()).toString()) < limit.doubleValue();
        } catch(Exception e) {
            return false;
        }
    }

}
