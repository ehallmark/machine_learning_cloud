package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

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

    @Override // don't want to update while ingesting
    public Collection<String> handleIncomingData(String item, Map<String, Object> data, Map<String,Collection<String>> myData, boolean isApplication) {
        return null;
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
    public Collection<String> attributesFor(Collection<String> items, int limit, Boolean isApp) {
        return Database.getGatherPatentToStagesCompleteMap().get(items.stream().findAny().get());
    }

    @Override
    public Collection<String> getAllValues() {
        return Constants.GATHER_STAGES;
    }

}
