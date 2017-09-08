package user_interface.ui_models.attributes.computable_attributes;

import j2html.tags.Tag;
import org.elasticsearch.index.query.QueryBuilder;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.ReelFrameAttribute;
import user_interface.ui_models.filters.AbstractBooleanIncludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import static j2html.TagCreator.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class ExistsInCompDBFilter extends AbstractIncludeFilter {
    private static Collection<String> MODEL;

    @Override
    public String getName() {
        return Constants.EXISTS_IN_COMPDB_FILTER;
    }

    public ExistsInCompDBFilter() {
        super(new AssetNumberAttribute(), FilterType.Include, FieldType.Boolean, runModel());
    }

    @Override
    public AbstractFilter dup() {
        return new ExistsInCompDBFilter();
    }

    private static Collection<String> runModel(){
        System.out.println("Starting to load ExistsInCompDBAttribute evaluator...");
        if(MODEL==null) {
            MODEL = Database.getCompDBAssets();
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

    public String getOptionGroup() {
        return Constants.COMPDB;
    }

}
