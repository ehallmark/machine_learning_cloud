package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToFilingMap extends HiddenAttribute<String> {
    @Override
    public String handleIncomingData(String name, Map<String, Object> data, boolean isApp) {
        Object filing = data.get(Constants.FILING_NAME);
        if(filing==null) return null;
        System.out.println("Filing: "+filing);
        return filing.toString();
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE+"_to_"+Constants.NAME;
    }
}
