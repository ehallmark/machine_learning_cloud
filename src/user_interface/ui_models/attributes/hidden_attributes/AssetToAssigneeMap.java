package user_interface.ui_models.attributes.hidden_attributes;

import com.google.gson.Gson;
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
    public AssetToAssigneeMap() {
        this.latestExecutionDateAttribute=new LatestExecutionDateAttribute();
        this.latestExecutionDateAttribute.initMaps();
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
            System.out.println("FOUND ASSIGNEE: "+ret);
            // check execution date
            // TODO check execution date
            Object executionDate = ((Map<String,Object>)allData.getOrDefault(Constants.ASSIGNMENTS,new HashMap<>())).get(Constants.EXECUTION_DATE);
            System.out.println("Execution date: "+executionDate);
            if(executionDate!=null) {
                // check
                if(!(executionDate instanceof LocalDate)) {
                    try {
                        executionDate = LocalDate.parse(executionDate.toString(), DateTimeFormatter.BASIC_ISO_DATE);
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

                // TODO remove this debug line below
                System.out.println("Updating execution date for "+name+": "+executionDate.toString());
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
