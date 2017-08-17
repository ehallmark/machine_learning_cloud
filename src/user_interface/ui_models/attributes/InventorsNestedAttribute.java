package user_interface.ui_models.attributes;

import lombok.Getter;
import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by Evan on 5/9/2017.
 */
public class InventorsNestedAttribute extends NestedAttribute {

    public InventorsNestedAttribute() {
        super(Arrays.asList(new FirstNameAttribute(), new LastNameAttribute(), new CityAttribute(), new CountryAttribute(), new StateAttribute()));
    }

    @Override
    public String getName() {
        return Constants.INVENTORS;
    }

}
