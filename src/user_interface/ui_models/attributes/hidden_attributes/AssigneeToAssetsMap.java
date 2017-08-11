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
    public Collection<String> handleIncomingData(String name, Map<String, Object> data, boolean isApp) {
        Object assignee = data.get(Constants.LATEST_ASSIGNEE);
        System.out.println("Latest assignee: "+assignee);
        if(assignee!=null&&name!=null) {
            Collection<String> currentAssets = isApp ? applicationDataMap.get(assignee) : patentDataMap.get(assignee);
            if(currentAssets==null) {
                currentAssets = new HashSet<>();
            }
            currentAssets.add(name);
            return currentAssets;
        }
        else return null;
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE+"_to_"+Constants.NAME;
    }
}
