package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToFilingMap extends BasicHiddenAttribute<String> {
    public AssetToFilingMap() {
        super(Constants.FILING_NAME);
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.FILING_NAME;
    }
}
