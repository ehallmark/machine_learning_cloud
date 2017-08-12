package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.time.LocalDate;
import java.util.Map;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToPriorityDate extends BasicHiddenAttribute<LocalDate> {
    public AssetToPriorityDate() {
        super(Constants.PRIORITY_DATE);
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.PRIORITY_DATE;
    }
}
