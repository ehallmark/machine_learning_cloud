package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.portfolios.items.Item;

import static j2html.TagCreator.button;
import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.extractString;

/**
 * Created by Evan on 7/9/2017.
 */
public class AdvancedKeywordFilter extends AbstractFilter {
    protected String queryStr;

    public AdvancedKeywordFilter(@NonNull AbstractAttribute<?> attribute, FilterType filterType) {
        super(attribute,filterType);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(queryStr==null) {
            return QueryBuilders.boolQuery();
        } else {
            return QueryBuilders.simpleQueryStringQuery(queryStr)
                    .defaultOperator(Operator.AND)
                    .analyzeWildcard(true) // might be slow but Scott Hicks uses it...
                    .field(getPrerequisite());
        }
    }

    public boolean isActive() {return queryStr!=null && queryStr.length()>0; }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        queryStr = String.join("", SimilarPatentServer.extractArray(req,getName()));
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                button("Syntax").withClass("miniTip btn btn-sm btn-secondary"),
                textarea().withClass("form-control").attr("placeholder","Example: (\"find this phrase\" | \"or this one\")").withName(getName())
        );
    }

    @Override
    public boolean contributesToScore() { return true; }
}
