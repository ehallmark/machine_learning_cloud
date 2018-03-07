package user_interface.ui_models.attributes;

import seeding.Constants;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class CompDBNestedAttribute extends NestedAttribute {

    public CompDBNestedAttribute() {
        super(Arrays.asList(new CompDBTechnologyAttribute(), new CompDBDealIDAttribute(), new ReelFrameAttribute(), new AssetNumberAttribute(), new InactiveAttribute(), new AcquisitionAttribute(), new BuyerAttribute(), new SellerAttribute(), new RecordedDateAttribute(), new NumCompDBAssignmentsAttribute(), new NumAssetsAttribute()));
    }

    @Override
    public String getName() {
        return Constants.COMPDB;
    }

}
