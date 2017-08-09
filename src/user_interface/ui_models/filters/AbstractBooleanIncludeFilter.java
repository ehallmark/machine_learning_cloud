package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractBooleanIncludeFilter extends AbstractFilter {
    public AbstractBooleanIncludeFilter(@NonNull AbstractAttribute<?> attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        return QueryBuilders.termQuery(getPrerequisite(), true);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        // do nothing
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
