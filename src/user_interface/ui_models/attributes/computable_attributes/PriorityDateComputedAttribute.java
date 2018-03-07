package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;

/**
 * Created by ehallmark on 7/20/17.
 */
public class PriorityDateComputedAttribute extends ComputableFilingAttribute<LocalDate> {
    private static final File priorityDateMapFile = new File(Constants.DATA_FOLDER+"patentPriorityDateByFilingMap.jobj");

    public PriorityDateComputedAttribute() {
        super(priorityDateMapFile,Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public String getName() {
        return Constants.PRIORITY_DATE;
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
