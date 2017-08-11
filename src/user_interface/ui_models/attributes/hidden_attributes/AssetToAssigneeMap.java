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
    public String handleIncomingData(String name, Map<String, Object> data, boolean isApp) {
        Object assignee = data.get(Constants.LATEST_ASSIGNEE);
        if(assignee==null) return null;
        return assignee.toString();
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE+"_to_"+Constants.NAME;
    }
}
