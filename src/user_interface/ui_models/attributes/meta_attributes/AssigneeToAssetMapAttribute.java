package user_interface.ui_models.attributes.meta_attributes;

import lombok.Getter;
import seeding.Constants;
import tools.DateHelper;
import user_interface.ui_models.attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by ehallmark on 8/10/17.
 */
public class AssigneeToAssetMapAttribute extends MetaComputableAttribute<Collection<String>> {
    @Override
    public Collection<String> handleIncomingData(String name, Map<String, Object> data, boolean isApp) {
        Object assignee = data.get(Constants.LATEST_ASSIGNEE);
        if(name!=null) {
            Collection<String> currentAssets = isApp ? applicationDataMap.get(assignee) : patentDataMap.get(assignee);
            if(currentAssets==null) {
                currentAssets = new HashSet<>();
            }
            currentAssets.add(name);
            return currentAssets;
        }
        else return null;
    }
}
