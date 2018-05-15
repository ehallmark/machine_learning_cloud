package user_interface.ui_models.engines;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import spark.Request;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;

import java.util.Collection;
import java.util.function.Function;

import static j2html.TagCreator.div;
import static user_interface.server.SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD;
import static user_interface.server.SimilarPatentServer.extractArray;

/**
 * Created by ehallmark on 2/28/17.
 */
public class AssigneeSimilarityEngine extends AbstractSimilarityEngine implements AjaxMultiselect {

    @Override
    public String getName() {
        return Constants.ASSIGNEE_SIMILARITY;
    }

    @Deprecated
    public AssigneeSimilarityEngine() {
        super();
    }

    public AssigneeSimilarityEngine(String tableName) {
        super(tableName, "name", "cpc_vae", false);
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
    public String getId() {
        return ASSIGNEES_TO_SEARCH_FOR_FIELD;
    }


    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                AbstractIncludeFilter.ajaxMultiSelect(getId(), ajaxUrl(), getId())
        );
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public AbstractSimilarityEngine dup() {
        if(isBigQuery) {
            return new AssigneeSimilarityEngine(tableName);
        } else {
            return new AssigneeSimilarityEngine();
        }
    }

    @Override
    public String ajaxUrl() {
        return Constants.ASSIGNEE_NAME_AJAX_URL;
    }

}
