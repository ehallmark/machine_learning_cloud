package user_interface.ui_models.filters;

import j2html.tags.Tag;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/13/2017.
 */
public abstract class AbstractBooleanExcludeFilter extends AbstractFilter {
    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        // do nothing

    }

    @Override
    public QueryBuilder getFilterQuery() {
        return QueryBuilders.termQuery(getPrerequisite(), false);
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        return !(Boolean)obj.getData(getPrerequisite());
    }


}
