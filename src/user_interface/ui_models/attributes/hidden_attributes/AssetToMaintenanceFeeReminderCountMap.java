package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.Map;
import java.util.Set;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToMaintenanceFeeReminderCountMap extends HiddenAttribute<Integer> {

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.MAINTENANCE_FEE_REMINDERS;
    }
}
