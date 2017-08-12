package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.time.LocalDate;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToFilingDateMap extends BasicHiddenAttribute<LocalDate> {
    public AssetToFilingDateMap() {
        super(Constants.FILING_DATE);
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.FILING_DATE;
    }
}
