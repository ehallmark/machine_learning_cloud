package user_interface.ui_models.filters;

import j2html.tags.Tag;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;
import static user_interface.server.SimilarPatentServer.extractString;

/**
 * Created by Evan on 7/9/2017.
 */
public class AdvancedKeywordFilter extends AbstractFilter {
    protected String queryStr;
    @Override
    public String getName() {
        return Constants.ADVANCED_KEYWORD_FILTER;
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(queryStr==null) {
            return QueryBuilders.boolQuery();
        } else {
            return QueryBuilders.simpleQueryStringQuery(queryStr)
                    .defaultOperator(Operator.AND)
                    .analyzeWildcard(false)
                    .field("tokens");
        }
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        queryStr = extractString(req,Constants.ADVANCED_KEYWORD_FILTER,null);
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","Example: (( smart <-> home ) | ( home <-> automation )) & ! lighting").withName(Constants.ADVANCED_KEYWORD_FILTER)
        );
    }

    @Override
    public boolean shouldKeepItem(Item obj) {
        return true;
    }

    @Override
    public String getPrerequisite() {
        return Constants.PORTFOLIO_SIZE;
    }
}
