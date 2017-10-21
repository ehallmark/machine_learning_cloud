package user_interface.ui_models.attributes.computable_attributes;

import assignee_normalization.NormalizeAssignees;
import lombok.NonNull;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToNormalizedAssigneeMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public abstract class ComputableNormalizedAssigneeAttribute<T> extends ComputableAssigneeAttribute<T> {
    public ComputableNormalizedAssigneeAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    @Override
    public Map<String,String> getAppToAssigneeMap() {
        if(appToAssigneeMap==null) {
            appToAssigneeMap = new AssetToNormalizedAssigneeMap().getApplicationDataMap();
        }
        return appToAssigneeMap;
    }

    @Override
    public Map<String,String> getPatentToAssigneeMap() {
        if(patentToAssigneeMap==null) {
            patentToAssigneeMap = new AssetToNormalizedAssigneeMap().getPatentDataMap();
        }
        return patentToAssigneeMap;
    }
}

