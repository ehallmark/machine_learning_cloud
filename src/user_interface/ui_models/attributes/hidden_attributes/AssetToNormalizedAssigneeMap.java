package user_interface.ui_models.attributes.hidden_attributes;

import assignee_normalization.NormalizeAssignees;
import seeding.Constants;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToNormalizedAssigneeMap extends AssetToAssigneeMap {
    private static NormalizeAssignees normalizer;
    public AssetToNormalizedAssigneeMap() {
        normalizer = new NormalizeAssignees();
    }
    @Override
    public String handleIncomingData(String name, Map<String,Object> allData, Map<String, String> myData, boolean isApp) {
        return null;
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.NORMALIZED_LATEST_ASSIGNEE;
    }

    public void normalize(AssetToAssigneeMap raw) {
        if(raw.getApplicationDataMap()!=null&&raw.getApplicationDataMap().size()>0) {
            applicationDataMap = normalizeHelper(raw.getApplicationDataMap());
        }
        if(raw.getPatentDataMap()!=null&&raw.getPatentDataMap().size()>0) {
            patentDataMap =  normalizeHelper(raw.getPatentDataMap());
        }
    }

    private Map<String,String> normalizeHelper(Map<String,String> raw) {
        Map<String,String> map = raw.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey(), e->normalizer.normalizedAssignee(e.getValue())));
        return Collections.synchronizedMap(map);
    }

    public void save() {
        NormalizedAssigneeToAssetsMap assigneeToAssetsMap = new NormalizedAssigneeToAssetsMap();
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

}
