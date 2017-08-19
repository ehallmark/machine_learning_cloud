package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public class LastExecutionDateAttribute extends ComputableAttribute<String> {

    public LastExecutionDateAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    public String handleIncomingData(String item, Map<String, Object> data, Map<String,String> myData, boolean isApplication) {
        if(item==null)return null;
        Object executionDate = data.get(Constants.EXECUTION_DATE);
        if(executionDate!=null) {
            String prev = myData.get(item);
            if (prev == null) {
                myData.put(item, executionDate.toString());
            } else {
                // compare
                LocalDate prevDate = LocalDate.parse(prev, DateTimeFormatter.ISO_DATE);
                LocalDate currDate = LocalDate.parse(executionDate.toString(), DateTimeFormatter.ISO_DATE);
                if(currDate.isAfter(prevDate)) {
                    // update
                    myData.put(item,executionDate.toString());
                }
            }
        }
        return null;
    }

    @Override
    public String getName() {
        return Constants.LAST_EXECUTION_DATE;
    }

    @Override
    public String getType() {
        return "date";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Date;
    }
}
