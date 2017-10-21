package user_interface.ui_models.attributes.computable_attributes;

import lombok.NonNull;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public abstract class ComputableAssigneeAttribute<T> extends ComputableAttribute<T> {
    protected Map<String,String> patentToAssigneeMap;
    protected Map<String,String> appToAssigneeMap;

    public Map<String,String> getAppToAssigneeMap() {
        if(appToAssigneeMap==null) {
            appToAssigneeMap = new AssetToAssigneeMap().getApplicationDataMap();
        }
        return appToAssigneeMap;
    }

    public Map<String,String> getPatentToAssigneeMap() {
        if(patentToAssigneeMap==null) {
            patentToAssigneeMap = new AssetToAssigneeMap().getPatentDataMap();
        }
        return patentToAssigneeMap;
    }

    public ComputableAssigneeAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    @Override
    public T attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return null;
        String item = portfolio.stream().filter(i->i!=null).findAny().orElse(null);
        if(item == null) return null;
        boolean probablyApplication = Database.isApplication(item);
        String assignee = probablyApplication ? getAppToAssigneeMap().getOrDefault(item,getPatentToAssigneeMap().get(item))
                : getPatentToAssigneeMap().getOrDefault(item,getAppToAssigneeMap().get(item));
        if(assignee == null) return null;
        return attributesForAssigneeHelper(assignee);
    }

    protected abstract T attributesForAssigneeHelper(@NonNull String assignee);

    public T attributesForAssignee(String assignee) {
        return attributesForAssigneeHelper(assignee);
    }

}

