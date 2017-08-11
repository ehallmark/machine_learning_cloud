package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToAssigneeMap extends HiddenAttribute<String> {
    @Override
    public String handleIncomingData(String name, Map<String,Object> allData, Map<String, String> myData, boolean isApp) {
        Object assignee = allData.get(Constants.LATEST_ASSIGNEE);
        if(assignee==null) return null;
        return assignee.toString();
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.ASSIGNEE;
    }
}
