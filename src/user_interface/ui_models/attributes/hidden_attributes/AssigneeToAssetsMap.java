package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssigneeToAssetsMap extends HiddenAttribute<Collection<String>> {
    @Override
    public Collection<String> handleIncomingData(String name, Map<String,Object> allData, Map<String, Collection<String>> myData, boolean isApp) {
        Object assignee = allData.get(Constants.LATEST_ASSIGNEE);
        if(assignee!=null&&name!=null) {
            Collection<String> currentAssets = myData.get(assignee);
            if(currentAssets==null) {
                currentAssets = new HashSet<>();
            }
            currentAssets.add(name);
            myData.put(assignee.toString(),currentAssets);
        }
        return null;
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE+"_to_"+Constants.NAME;
    }
}
