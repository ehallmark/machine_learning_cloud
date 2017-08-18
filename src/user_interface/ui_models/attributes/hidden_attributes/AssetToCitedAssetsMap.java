package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToCitedAssetsMap extends HiddenAttribute<Collection<String>> {
    @Override
    public Collection<String> handleIncomingData(String name, Map<String,Object> allData, Map<String, Collection<String>> myData, boolean isApp) {
        List<Map<String,Object>> citedAssets = (List<Map<String,Object>>) allData.get(Constants.CITATIONS);
        if(citedAssets!=null&&name!=null) {
            Collection<String> currentFamily = myData.get(name);
            if(currentFamily==null) {
                currentFamily = new HashSet<>();
            }
            Collection<String> related = citedAssets.stream().map(map->(String) map.get(Constants.NAME)).filter(n->n!=null).collect(Collectors.toList());
            currentFamily.addAll(related);
            return currentFamily;
        }
        return null;
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.CITATIONS;
    }
}
