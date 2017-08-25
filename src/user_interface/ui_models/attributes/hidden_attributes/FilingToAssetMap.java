package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class FilingToAssetMap extends HiddenAttribute<Collection<String>> {
    @Override
    public Collection<String> handleIncomingData(String name, Map<String,Object> allData, Map<String, Collection<String>> myData, boolean isApp) {
        Object filing = allData.get(Constants.FILING_NAME);
        if(filing==null) return null;
        if(name!=null) {
            Collection<String> previous = myData.get(filing.toString());
            if(previous==null) {
                previous = Collections.synchronizedSet(new HashSet<>());
                myData.put(filing.toString(), previous);
            }
            previous.add(name);
        }
        return null;
    }

    @Override
    public String getName() {
        return Constants.FILING_NAME+"_to_"+Constants.NAME+"_set";
    }
}
