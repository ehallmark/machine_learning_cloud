package user_interface.ui_models.attributes.hidden_attributes;

import lombok.Getter;
import lombok.Setter;
import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.LatestExecutionDateAttribute;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToAssigneeMap extends HiddenAttribute<String> {
    private LatestExecutionDateAttribute latestExecutionDateAttribute;
    @Getter @Setter
    private static Map<String,Boolean> assigneeToHumanMap;
    public AssetToAssigneeMap() {
        this.latestExecutionDateAttribute=new LatestExecutionDateAttribute();
    }

    @Override
    public String handleIncomingData(String name, Map<String,Object> allData, Map<String, String> myData, boolean isApp) {
        Object assignee = allData.get(Constants.LATEST_ASSIGNEE);
        Object ret = null;
        if(assignee!=null&&(assignee instanceof Map || assignee instanceof List)) {
            if(assignee instanceof List) {
                if(((List) assignee).size() == 0) return null;
                assignee = ((List)assignee).get(0);
            }
            ret = ((Map<String,Object>)assignee).get(Constants.ASSIGNEE);

            Boolean isHuman = (Boolean)((Map<String,Object>)assignee).get(Constants.IS_HUMAN);
            if(isHuman!=null) {
                if(ret!=null) {
                    if(assigneeToHumanMap!=null) {
                        assigneeToHumanMap.put(ret.toString(),isHuman);
                    }
                }
            }
            //System.out.println("FOUND ASSIGNEE: "+ret);
            // check execution date
            Object executionDate = ((Map<String,Object>)allData.getOrDefault(Constants.ASSIGNMENTS,new HashMap<>())).get(Constants.EXECUTION_DATE);

            if(executionDate!=null) {
                // check
                if(!(executionDate instanceof LocalDate)) {
                    try {
                        executionDate = LocalDate.parse(executionDate.toString(), DateTimeFormatter.ISO_DATE);
                    } catch(Exception e) {
                        System.out.println("Error parsing execution date: "+executionDate);
                        executionDate=null;
                    }
                }
            }
            if(executionDate != null) {
                // is a localdate
                Map<String,LocalDate> latestExecutionDateMap = isApp ? latestExecutionDateAttribute.getApplicationDataMap() : latestExecutionDateAttribute.getPatentDataMap();
                // check previous
                LocalDate priorExecutionDate = latestExecutionDateMap.get(name);
                if(priorExecutionDate != null) {
                    // check date order
                    if(priorExecutionDate.isAfter((LocalDate)executionDate)) {
                        return null; // don't update!
                    }
                }
                // update latest execution date map
                latestExecutionDateMap.put(name, (LocalDate)executionDate);
            }

            if(executionDate == null) {
                // don't update unless nothing is there
                if(myData.containsKey(name)) {
                    return null;
                }
            }
        }
        if(ret==null) return null;
        return ret.toString();
    }

    @Override
    public boolean shouldCleanseBeforeReseed() {
        return true;
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.LATEST_ASSIGNEE;
    }

    @Override
    public void save() {
        AssigneeToAssetsMap assigneeToAssetsMap = new AssigneeToAssetsMap();
        assigneeToAssetsMap.initMaps();
        if(patentDataMap!=null && patentDataMap.size()>0) {
            synchronized (AssetToAssigneeMap.class) {
                safeSaveFile(patentDataMap, dataFileFrom(Constants.PATENT_DATA_FOLDER, getName(), getType()));
                assigneeToAssetHelper(patentDataMap,assigneeToAssetsMap.getPatentDataMap());
            }
        }
        if(applicationDataMap!=null && applicationDataMap.size()>0) {
            synchronized (AssetToAssigneeMap.class) {
                safeSaveFile(applicationDataMap, dataFileFrom(Constants.APPLICATION_DATA_FOLDER,getName(),getType()));
                assigneeToAssetHelper(applicationDataMap,assigneeToAssetsMap.getApplicationDataMap());
            }
        }
        assigneeToAssetsMap.save();
        // save normalizations
        AssetToNormalizedAssigneeMap normalized = new AssetToNormalizedAssigneeMap();
        normalized.normalize(this);
        normalized.save();

        // save latest execution date
        System.out.println("Saving latest execution date map...");
        latestExecutionDateAttribute.save();
        System.out.println("Saved.");
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
