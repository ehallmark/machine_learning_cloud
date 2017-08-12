package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.time.LocalDate;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToPubDateMap extends BasicHiddenAttribute<LocalDate> {
    public AssetToPubDateMap() {
        super(Constants.PUBLICATION_DATE);
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.PUBLICATION_DATE;
    }
}
