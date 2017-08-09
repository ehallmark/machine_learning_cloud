package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class AssigneesNestedAttribute extends NestedAttribute {

    public AssigneesNestedAttribute() {
        super(Arrays.asList(new OrgNameAttribute(), new OrgRoleAttribute(), new FirstNameAttribute(), new LastNameAttribute(), new CityAttribute(), new CountryAttribute(), new StateAttribute()));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEES;
    }

    @Override
    public String getType() {
        return "nested";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.NestedObject;
    }
}
