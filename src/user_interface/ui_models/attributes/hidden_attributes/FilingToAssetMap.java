package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class FilingToAssetMap extends HiddenAttribute<String> {
    @Override
    public String handleIncomingData(String name, Map<String,Object> allData, Map<String, String> myData, boolean isApp) {
        Object filing = allData.get(Constants.FILING_NAME);
        if(filing==null) return null;
        System.out.println("Filing: "+filing);
        if(name!=null) {
            myData.put(filing.toString(),name);
        }
        return null;
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE+"_to_"+Constants.NAME;
    }
}
