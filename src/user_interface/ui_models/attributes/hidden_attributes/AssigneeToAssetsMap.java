package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;

import java.io.File;
import java.util.*;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssigneeToAssetsMap extends HiddenAttribute<Collection<String>> {
    @Override
    public Collection<String> handleIncomingData(String name, Map<String,Object> allData, Map<String, Collection<String>> myData, boolean isApp) {
        return null;
    }

    @Override
    public String getName() {
        return Constants.LATEST_ASSIGNEE+"_to_"+Constants.NAME;
    }

}
