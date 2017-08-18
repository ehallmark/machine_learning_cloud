package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetEntityStatusMap extends HiddenAttribute<Set<String>> {

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.ASSIGNEE_ENTITY_TYPE;
    }

    @Override
    public synchronized Map<String,Set<String>> getPatentDataMap() {
        patentDataMap = super.getPatentDataMap();
        if(patentDataMap!=null&&patentDataMap.isEmpty()) {
            patentDataMap.put(Constants.SMALL,new HashSet<>());
            patentDataMap.put(Constants.MICRO,new HashSet<>());
            patentDataMap.put(Constants.LARGE,new HashSet<>());
        }
        return patentDataMap;
    }

    @Override
    public synchronized Map<String,Set<String>> getApplicationDataMap() {
        applicationDataMap = super.getApplicationDataMap();
        if(applicationDataMap!=null&&applicationDataMap.isEmpty()) {
            applicationDataMap.put(Constants.SMALL,new HashSet<>());
            applicationDataMap.put(Constants.MICRO,new HashSet<>());
            applicationDataMap.put(Constants.LARGE,new HashSet<>());
        }
        return applicationDataMap;
    }
}
