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
import static user_interface.server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class CPCSimilarityEngine extends AbstractSimilarityEngine implements AjaxMultiselect {

    public CPCSimilarityEngine(String tableName) {
        super(tableName, Attributes.CODE, Attributes.ENC, false);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req) {
        System.out.println("Collecting inputs to search for...");
        // get input data
        Collection<String> inputsToSearchFor = extractArray(req, getId());

        System.out.println("Found "+inputsToSearchFor.size()+" cpc to search for.");
        return inputsToSearchFor;
    }

    @Override
    public String getId() {
        return CPCS_TO_SEARCH_FOR_FIELD;
    }

    @Override
    public String getName() {
        return Constants.CPC_SIMILARITY;
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
        return new CPCSimilarityEngine(tableName);
    }

    @Override
    public String ajaxUrl() {
        return Constants.CPC_CODE_AJAX_URL;
    }
}
