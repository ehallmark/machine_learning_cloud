package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.time.LocalDate;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToTermAdjustmentMap extends BasicHiddenAttribute<Integer> {
    public AssetToTermAdjustmentMap() {
        super(Constants.PATENT_TERM_ADJUSTMENT);
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.PATENT_TERM_ADJUSTMENT;
    }
}
