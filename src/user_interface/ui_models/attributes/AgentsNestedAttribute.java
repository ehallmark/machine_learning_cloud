package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class AgentsNestedAttribute extends NestedAttribute {

    public AgentsNestedAttribute() {
        super(Arrays.asList(new FirstNameAttribute(), new LastNameAttribute(), new CityAttribute(), new CountryAttribute(), new StateAttribute()));
    }

    @Override
    public String getName() {
        return Constants.AGENTS;
    }

}
