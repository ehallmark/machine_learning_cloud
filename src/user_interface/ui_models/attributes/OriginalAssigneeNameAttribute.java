package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class OriginalAssigneeNameAttribute extends StreamableAttribute<String[]> {
    public OriginalAssigneeNameAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }
    @Override
    public String getType() {
        return "text";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE;
    }

}
