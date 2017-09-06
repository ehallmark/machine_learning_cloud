package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public class CompDBDealIDAttribute extends ComputableAttribute<String> {

    public CompDBDealIDAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include,AbstractFilter.FilterType.Exclude));
    }

    @Override
    public String attributesFor(Collection<String> items, int limit) {
        return Database.getCompDBAssetToDealIDMap().get(items.stream().findAny().get());
    }

    @Override
    public String getName() {
        return Constants.COMPDB_DEAL_ID;
    }

    @Override
    public String getType() {
        return "keyword";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

}

