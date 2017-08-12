package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Evan on 8/11/2017.
 */
public class ExpiredAssetsMap extends HiddenAttribute<Set<String>> {
    @Override
    public Set<String> handleIncomingData(String name, Map<String,Object> allData, Map<String, Set<String>> myData, boolean isApp) {
        return null;
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.EXPIRED;
    }

    @Override
    public synchronized Map<String,Set<String>> getPatentDataMap() {
        patentDataMap = super.getPatentDataMap();
        if(patentDataMap!=null&&patentDataMap.isEmpty()) {
            patentDataMap.put(Constants.EXPIRED,new HashSet<>());
        }
        return patentDataMap;
    }

    @Override
    public synchronized Map<String,Set<String>> getApplicationDataMap() {
        applicationDataMap = super.getApplicationDataMap();
        if(applicationDataMap!=null&&applicationDataMap.isEmpty()) {
            applicationDataMap.put(Constants.EXPIRED,new HashSet<>());
        }
        return applicationDataMap;
    }
}
