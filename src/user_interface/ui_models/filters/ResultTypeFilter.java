package user_interface.ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.stream.Collectors;


/**
 * Created by Evan on 7/24/2017.
 */
public class ResultTypeFilter extends AbstractIncludeFilter {
    @Override
    public String getName() {
        return Constants.RESULT_TYPE_FILTER;
    }

    @Override
    public Tag getOptionsTag() {
        return SimilarPatentServer.technologySelect(SimilarPatentServer.SEARCH_TYPE_ARRAY_FIELD,Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList()));
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        labels = SimilarPatentServer.extractArray(params, SimilarPatentServer.SEARCH_TYPE_ARRAY_FIELD);
    }

    @Override
    public String getPrerequisite() {
        return Constants.DOC_TYPE;
    }
}
