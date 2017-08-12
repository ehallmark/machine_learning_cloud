package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToRelatedAssetsMap extends HiddenAttribute<Collection<String>> {
    @Override
    public Collection<String> handleIncomingData(String name, Map<String,Object> allData, Map<String, Collection<String>> myData, boolean isApp) {
        Object relatedAsset = allData.get(Constants.PATENT_FAMILY);
        if(relatedAsset!=null&&name!=null) {
            Collection<String> currentFamily = myData.get(name);
            if(currentFamily==null) {
                currentFamily = new HashSet<>();
            }
            currentFamily.add(relatedAsset.toString());
            return currentFamily;
        }
        return null;
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.PATENT_FAMILY;
    }
}
