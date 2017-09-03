package user_interface.ui_models.attributes.computable_attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.ReelFrameAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;

import java.util.ArrayList;
import java.util.Set;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 1/27/2017.
 */
public class ExistsInGatherFilter extends AbstractIncludeFilter {
    private static Set<String> MODEL;

    @Override
    public String getName() {
        return Constants.EXISTS_IN_GATHER_FILTER;
    }

    public ExistsInGatherFilter() {
        super(new AssetNumberAttribute(), FilterType.Include, FieldType.Boolean, runModel());
    }

    @Override
    public AbstractFilter dup() {
        return new ExistsInGatherFilter();
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
    public Tag getOptionsTag() {
        return div();
    }

}
