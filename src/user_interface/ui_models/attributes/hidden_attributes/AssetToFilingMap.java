package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToFilingMap extends HiddenAttribute<String> {
    @Override
    public String handleIncomingData(String name, Map<String,Object> allData, Map<String, String> myData, boolean isApp) {
        Object filing = allData.get(Constants.FILING_NAME);
        if(filing==null) return null;
        return filing.toString();
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.FILING_NAME;
    }
}
