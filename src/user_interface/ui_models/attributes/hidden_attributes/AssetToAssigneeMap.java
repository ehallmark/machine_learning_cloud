package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.LastExecutionDateAttribute;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToAssigneeMap extends HiddenAttribute<String> {
    private LastExecutionDateAttribute lastExecutionDate = new LastExecutionDateAttribute();
    @Override
    public String handleIncomingData(String name, Map<String,Object> allData, Map<String, String> myData, boolean isApp) {
        Object assignee = allData.get(Constants.LATEST_ASSIGNEE);
        if(assignee!=null&&assignee instanceof Map) {
            assignee = ((Map<String,Object>)assignee).get(Constants.ASSIGNEE);
            // check execution date
            // TODO check execution date

        }
        if(assignee==null) return null;
        return assignee.toString();
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.LATEST_ASSIGNEE;
    }

    public void save() {
        AssigneeToAssetsMap assigneeToAssetsMap = new AssigneeToAssetsMap();
        assigneeToAssetsMap.initMaps();
        if(patentDataMap!=null && patentDataMap.size()>0) {
            synchronized (patentDataMap) {
                safeSaveFile(patentDataMap, dataFileFrom(Constants.PATENT_DATA_FOLDER, getName(), getType()));
                assigneeToAssetHelper(patentDataMap,assigneeToAssetsMap.getPatentDataMap());
            }
        }
        if(applicationDataMap!=null && applicationDataMap.size()>0) {
            synchronized (applicationDataMap) {
                safeSaveFile(applicationDataMap, dataFileFrom(Constants.APPLICATION_DATA_FOLDER,getName(),getType()));
                assigneeToAssetHelper(applicationDataMap,assigneeToAssetsMap.getApplicationDataMap());
            }
        }
        assigneeToAssetsMap.save();
    }

    private void assigneeToAssetHelper(Map<String,String> assetToAssignee, Map<String,Collection<String>> assigneeToAssets) {
        assigneeToAssets.clear();
        assetToAssignee.entrySet().parallelStream().forEach(e->{
            if(assigneeToAssets.containsKey(e.getValue())) {
                assigneeToAssets.get(e.getValue()).add(e.getKey());
            } else {
                Collection<String> set = new HashSet<>();
                set.add(e.getKey());
                assigneeToAssets.put(e.getValue(),set);
            }
        });
    }
}
