package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public class GatherBoolValueAttribute extends ComputableAttribute<Boolean> {

    public GatherBoolValueAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }

    @Override
    public Boolean attributesFor(Collection<String> items, int limit, Boolean isApp) {
        return Database.getGatherValueMap().get(items.stream().findAny().get());
    }

    @Override // don't want to update while ingesting
    public Boolean handleIncomingData(String item, Map<String, Object> data, Map<String,Boolean> myData, boolean isApplication) {
        return null;
    }

    @Override
    public String getName() {
        return Constants.GATHER_VALUE;
    }

    @Override
    public String getType() {
        return "boolean";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

}

