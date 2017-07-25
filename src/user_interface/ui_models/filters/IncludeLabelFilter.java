package user_interface.ui_models.filters;

import j2html.tags.Tag;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;

import static j2html.TagCreator.*;
import static user_interface.server.SimilarPatentServer.PATENTS_TO_SEARCH_IN_FIELD;
import static user_interface.server.SimilarPatentServer.extractString;
import static user_interface.server.SimilarPatentServer.preProcess;

/**
 * Created by ehallmark on 5/10/17.
 */
public class IncludeLabelFilter extends AbstractIncludeFilter {
    public IncludeLabelFilter() {}

    public IncludeLabelFilter(Collection<String> labels) {
        this.labels=labels;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","1 patent or application per line (eg. 800000)").withName(SimilarPatentServer.PATENTS_TO_SEARCH_IN_FIELD)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        labels = preProcess(extractString(req, PATENTS_TO_SEARCH_IN_FIELD, ""), "\\s+", "[^0-9]");
    }

    @Override
    public String getName() {
        return Constants.PATENT_SEARCH_SCOPE_FILTER;
    }


    @Override
    public String getPrerequisite() {
        return Constants.NAME;
    }


    @Override
    public QueryBuilder getFilterQuery() {
        QueryBuilder builder = QueryBuilders.idsQuery().addIds(labels.toArray(new String[labels.size()]));
        return builder;
    }

}
