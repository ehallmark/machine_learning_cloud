package user_interface.ui_models.attributes.computable_attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;

import java.util.Set;
import java.util.function.Function;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 1/27/2017.
 */
public class DoesNotExistInGatherFilter extends AbstractIncludeFilter {
    private static Set<String> MODEL;

    @Override
    public String getName() {
        return Constants.DOES_NOT_EXIST_IN_GATHER_FILTER;
    }

    public DoesNotExistInGatherFilter() {
        super(new AssetNumberAttribute(), FilterType.Exclude, FieldType.Boolean, runModel());
    }

    @Override
    public AbstractFilter dup() {
        return new DoesNotExistInGatherFilter();
    }


    private static Set<String> runModel(){
        if(MODEL==null) {
            MODEL = Database.getGatherAssets();
        }
        return MODEL;
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {

    }
    @Override
    public Tag getDescription() {
        return div().withText("This filter only includes results that do not exist in Gather.");
    }


    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div();
    }

    public String getOptionGroup() {
        return Constants.GATHER;
    }

}
