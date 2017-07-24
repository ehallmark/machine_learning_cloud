package user_interface.ui_models.filters;

import j2html.tags.Tag;
import org.elasticsearch.index.query.BoolQueryBuilder;
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
public class ExcludeKeywordFilter extends AbstractFilter {
    protected String queryStr;
    @Override
    public String getName() {
        return Constants.EXCLUDE_KEYWORD_FILTER;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","Enter keywords to exclude").withName(Constants.EXCLUDE_KEYWORD_FILTER)
        );
    }
    @Override
    public boolean shouldKeepItem(Item obj) {
        return true;
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if(queryStr==null) {
            return QueryBuilders.boolQuery();
        } else {
            // includes
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
            String[] includePhrases = queryStr.split("\\n");
            for(String phrase: includePhrases) {
                queryBuilder = queryBuilder.mustNot(QueryBuilders.matchPhraseQuery("tokens",phrase.trim()));
            }
            return queryBuilder;
        }
    }

    @Override
    public String getPrerequisite() {
        return null;
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        queryStr = extractString(req,Constants.EXCLUDE_KEYWORD_FILTER,null);
    }



}
