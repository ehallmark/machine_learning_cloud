package user_interface.ui_models.attributes;

import seeding.Constants;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class AssigneesNestedAttribute extends NestedAttribute {

    public AssigneesNestedAttribute() {
        super(Arrays.asList(new AssigneeNameAttribute(), new JapaneseAttribute(), new OrgRoleAttribute(), new FirstNameAttribute(), new LastNameAttribute(), new CityAttribute(), new CountryAttribute(), new StateAttribute()));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEES;
    }


}
