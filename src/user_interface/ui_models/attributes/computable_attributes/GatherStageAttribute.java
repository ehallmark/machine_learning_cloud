package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by ehallmark on 6/15/17.
 */
public class GatherStageAttribute extends ComputableAttribute<Collection<String>> {
    public GatherStageAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }
    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

    @Override
    public String getName() {
        return Constants.GATHER_STAGE;
    }

    @Override
    public Collection<String> attributesFor(Collection<String> items, int limit) {
        return Database.getGatherPatentToStagesCompleteMap().get(items.stream().findAny().get());
    }

    @Override
    public Collection<String> getAllValues() {
        return Constants.GATHER_STAGES;
    }

}
