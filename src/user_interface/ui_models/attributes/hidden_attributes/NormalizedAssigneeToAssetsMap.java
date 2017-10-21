package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Collection;
import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class NormalizedAssigneeToAssetsMap extends AssigneeToAssetsMap {

    @Override
    public String getName() {
        return Constants.NORMALIZED_LATEST_ASSIGNEE+"_to_"+Constants.NAME;
    }

}
