package user_interface.ui_models.attributes;

import seeding.Constants;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class ClaimsNestedAttribute extends NestedAttribute {
    public ClaimsNestedAttribute() {
        super(Arrays.asList(new ClaimNumberAttribute(), new ClaimTextAttribute(), new ClaimLengthAttribute(), new ParentClaimNumAttribute()));
    }

    @Override
    public String getName() {
        return Constants.CLAIMS;
    }

}
