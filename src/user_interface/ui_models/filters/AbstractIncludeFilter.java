package user_interface.ui_models.filters;

import j2html.tags.Tag;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/17/2017.
 */
public abstract class AbstractIncludeFilter extends AbstractFilter {
    protected Collection<String> labels;

    @Override
    public QueryBuilder getFilterQuery() {
        QueryBuilder builder = QueryBuilders.termsQuery(getPrerequisite(),labels);
        return builder;
    }

    public boolean isActive() { return labels.size() > 0; }

    @Override
    public boolean shouldKeepItem(Item item) {
        return labels==null||labels.isEmpty()||labels.contains(item.getData(getPrerequisite()));
    }
}
