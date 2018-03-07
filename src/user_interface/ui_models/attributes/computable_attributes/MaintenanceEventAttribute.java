package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

/**
 * Created by Evan on 6/18/2017.
 */
public class MaintenanceEventAttribute extends ComputableFilingAttribute<Set<String>> {
    private static final File maintenanceEventsFile = new File(Constants.DATA_FOLDER+"patentMaintenanceFeeEvents.jobj");
    public MaintenanceEventAttribute() {
        super(maintenanceEventsFile,Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude, AbstractFilter.FilterType.Exists, AbstractFilter.FilterType.DoesNotExist));
    }

    @Override
    public String getName() {
        return Constants.MAINTENANCE_EVENT;
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }
}
