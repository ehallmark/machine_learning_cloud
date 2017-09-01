package user_interface.ui_models.attributes.computable_attributes;

import lombok.NonNull;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Created by ehallmark on 6/15/17.
 */
public class GatherValueAttribute extends ComputableAttribute<Integer> {

    public GatherValueAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public Integer attributesFor(Collection<String> items, int limit) {
        return Database.getGatherValueMap().get(items.stream().findAny().get());
    }

    @Override
    public String getName() {
        return Constants.GATHER_VALUE;
    }

    @Override
    public String getType() {
        return "integer";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

}

