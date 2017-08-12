package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.PortfolioSizeAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public class LatestAssigneeNestedAttribute extends NestedAttribute {
    public LatestAssigneeNestedAttribute() {
        super(Arrays.asList(new EntityTypeAttribute(), new ExecutionDateAttribute(), new AssigneeNameAttribute(), new PortfolioSizeAttribute(), new OrgRoleAttribute(), new FirstNameAttribute(), new LastNameAttribute(), new CityAttribute(), new CountryAttribute(), new StateAttribute()));
    }

    @Override
    public String getType() {
        return "nested";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.NestedObject;
    }

    @Override
    public String getName() {
        return Constants.LATEST_ASSIGNEE;
    }
}
