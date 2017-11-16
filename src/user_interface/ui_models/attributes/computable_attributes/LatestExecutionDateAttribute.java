package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

/**
 * Created by ehallmark on 7/20/17.
 */
public class LatestExecutionDateAttribute extends ComputableAttribute<LocalDate> {
    public LatestExecutionDateAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public LocalDate handleIncomingData(String name, Map<String,Object> allData, Map<String, LocalDate> myData, boolean isApp) {
        return null;
    }

    @Override
    public String getName() {
        return Constants.EXECUTION_DATE;
    }

    @Override
    public String getType() {
        return "date";
    }

    public boolean shouldCleanseBeforeReseed() {
        return true;
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Date;
    }
}
