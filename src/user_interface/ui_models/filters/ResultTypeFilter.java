package user_interface.ui_models.filters;

import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static j2html.TagCreator.option;
import static j2html.TagCreator.select;

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
        return select().withClass("form-control single-select2").withName(SimilarPatentServer.SEARCH_TYPE_FIELD).with(
                Arrays.stream(PortfolioList.Type.values()).map(type->{
                    ContainerTag option = option(SimilarPatentServer.humanAttributeFor(type.toString())).withValue(type.toString());
                    if(type.equals(PortfolioList.Type.patents)) option=option.attr("selected","selected");
                    return option;
                }).collect(Collectors.toList())
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        labels = new ArrayList<>(2);
        String searchType = SimilarPatentServer.extractString(params, SimilarPatentServer.SEARCH_TYPE_FIELD, PortfolioList.Type.patents.toString());
        PortfolioList.Type portfolioType = PortfolioList.Type.valueOf(searchType);
        if(portfolioType.equals(PortfolioList.Type.assets)) {
            labels.add(PortfolioList.Type.patents.toString());
            labels.add(PortfolioList.Type.applications.toString());
        } else {
            labels.add(portfolioType.toString());
        }
    }

    @Override
    public String getPrerequisite() {
        return "doc_type";
    }
}
