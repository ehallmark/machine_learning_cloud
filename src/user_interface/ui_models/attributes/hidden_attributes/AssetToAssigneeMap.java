package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.*;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToAssigneeMap extends HiddenAttribute<String> {
    @Override
    public String handleIncomingData(String name, Map<String,Object> allData, Map<String, String> myData, boolean isApp) {
        Object assignee = allData.get(Constants.LATEST_ASSIGNEE);
        if(assignee!=null&&(assignee instanceof Map || assignee instanceof List)) {
            if(assignee instanceof List) {
                if(((List) assignee).size() == 0) return null;
                assignee = ((List)assignee).get(0);
            }
            assignee = ((Map<String,Object>)assignee).get(Constants.ASSIGNEE);
            //System.out.println("FOUND ASSIGNEE: "+assignee);
            // check execution date
            // TODO check execution date

        }
        if(assignee==null) return null;
        return assignee.toString();
    }

    @Override
    public boolean shouldCleanseBeforeReseed() {
        return true;
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
        // save normalizations
        AssetToNormalizedAssigneeMap normalized = new AssetToNormalizedAssigneeMap();
        normalized.normalize(this);
        normalized.save();
    }

    protected void assigneeToAssetHelper(Map<String,String> assetToAssignee, Map<String,Collection<String>> assigneeToAssets) {
        assigneeToAssets.clear();
        assetToAssignee.entrySet().parallelStream().forEach(e->{
            if(assigneeToAssets.containsKey(e.getValue())) {
                assigneeToAssets.get(e.getValue()).add(e.getKey());
            } else {
                Collection<String> set = Collections.synchronizedSet(new HashSet<>());
                set.add(e.getKey());
                assigneeToAssets.put(e.getValue(),set);
            }
        });
    }
}
