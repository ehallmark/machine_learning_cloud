package user_interface.ui_models.engines;

import j2html.tags.Tag;
import models.similarity_models.AbstractSimilarityModel;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;

import static j2html.TagCreator.*;
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class AssigneeSimilarityEngine extends AbstractSimilarityEngine implements AjaxMultiselect {

    @Override
    public String getName() {
        return Constants.ASSIGNEE_SIMILARITY;
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req, Collection<String> resultTypes) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> inputsToSearchFor = extractArray(req, ASSIGNEES_TO_SEARCH_FOR_FIELD);

        System.out.println("Found "+inputsToSearchFor.size()+" assignees to search for.");
        return inputsToSearchFor;
    }


    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                AbstractIncludeFilter.ajaxMultiSelect(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD, ajaxUrl(), SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD)
        );
        //return div().with(
        //        textarea().withClass("form-control").attr("placeholder","1 assignee per line").withId(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD).withName(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD)
        //);
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public AbstractSimilarityEngine dup() {
        return new AssigneeSimilarityEngine();
    }

    @Override
    public String ajaxUrl() {
        return Constants.ASSIGNEE_NAME_AJAX_URL;
    }

}
