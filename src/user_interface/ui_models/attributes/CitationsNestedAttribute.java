package user_interface.ui_models.attributes;

import seeding.Constants;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class CitationsNestedAttribute extends NestedAttribute {

    public CitationsNestedAttribute() {
        super(Arrays.asList(new AssetNumberAttribute(), new DocKindAttribute(), new CitedDateAttribute(), new CountryAttribute()));
    }

    @Override
    public String getName() {
        return Constants.CITATIONS;
    }

}
