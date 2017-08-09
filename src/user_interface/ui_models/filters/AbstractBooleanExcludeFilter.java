package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/13/2017.
 */
public class AbstractBooleanExcludeFilter extends AbstractFilter {
    public AbstractBooleanExcludeFilter(@NonNull AbstractAttribute<?> attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        return QueryBuilders.termQuery(getPrerequisite(), false);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
