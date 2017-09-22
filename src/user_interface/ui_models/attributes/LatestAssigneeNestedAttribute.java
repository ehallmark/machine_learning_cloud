package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.CompDBAssetsPurchasedAttribute;
import user_interface.ui_models.attributes.computable_attributes.CompDBAssetsSoldAttribute;
import user_interface.ui_models.attributes.computable_attributes.PortfolioSizeAttribute;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class LatestAssigneeNestedAttribute extends NestedAttribute {
    public LatestAssigneeNestedAttribute() {
        super(Arrays.asList(new CompDBAssetsPurchasedAttribute(),new CompDBAssetsSoldAttribute(), new EntityTypeAttribute(), new ExecutionDateAttribute(), new AssigneeNameAttribute(), new PortfolioSizeAttribute(), new OrgRoleAttribute(), new FirstNameAttribute(), new LastNameAttribute(), new CityAttribute(), new CountryAttribute(), new StateAttribute()));
        this.isObject=true;
    }
    
    @Override
    public String getName() {
        return Constants.LATEST_ASSIGNEE;
    }

}
