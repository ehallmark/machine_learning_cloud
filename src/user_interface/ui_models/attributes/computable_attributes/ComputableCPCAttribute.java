package user_interface.ui_models.attributes.computable_attributes;

import lombok.NonNull;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 6/15/17.
 */
public abstract class ComputableCPCAttribute<T> extends ComputableAttribute<T> {
    protected Map<String,Set<String>> patentToCPCMap;
    protected Map<String,Set<String>> appToCPCMap;

    public Map<String,Set<String>> getAppToCPCMap() {
        if(appToCPCMap==null) {
            appToCPCMap = new AssetToCPCMap().getApplicationDataMap();
        }
        return appToCPCMap;
    }

    public Map<String,Set<String>> getPatentToCPCMap() {
        if(patentToCPCMap==null) {
            patentToCPCMap = new AssetToCPCMap().getPatentDataMap();
        }
        return patentToCPCMap;
    }

    public ComputableCPCAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    @Override
    public T attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return null;
        String item = portfolio.stream().filter(i->i!=null).findAny().orElse(null);
        if(item == null) return null;
        boolean probablyApplication = Database.isApplication(item);
        Set<String> cpcs = probablyApplication ? getAppToCPCMap().getOrDefault(item,getPatentToCPCMap().get(item))
                : getPatentToCPCMap().getOrDefault(item,getAppToCPCMap().get(item));
        if(cpcs == null) return null;
        return attributesforCPCsHelper(cpcs);
    }

    protected abstract T attributesforCPCsHelper(@NonNull Collection<String> cpcs);

    @Override // don't want to update while ingesting
    public T handleIncomingData(String item, Map<String, Object> data, Map<String,T> myData, boolean isApplication) {
        return null;
    }
}

