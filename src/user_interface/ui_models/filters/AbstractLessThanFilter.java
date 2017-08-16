package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;
import static j2html.TagCreator.input;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractLessThanFilter extends AbstractFilter {
    protected Number limit;

    public AbstractLessThanFilter(@NonNull AbstractAttribute<?> attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(limit == null || limit.doubleValue() <= 0d) {
            return QueryBuilders.boolQuery();
        } else {
            return QueryBuilders.rangeQuery(getFullPrerequisite())
                    .lt(limit);
        }
    }

    public boolean isActive() { return limit!=null && limit.doubleValue() > 0d; }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        this.limit = SimilarPatentServer.extractDoubleFromArrayField(params,getName(),null);
        System.out.println("Filter "+getName()+": less than "+limit);
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                input().withClass("form-control").withType("number").withValue("0").withName(getName())
        );
    }
}
