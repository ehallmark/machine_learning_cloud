package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.*;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class LatestAssigneeNestedAttribute extends NestedAttribute {
    public LatestAssigneeNestedAttribute() {
        super(Arrays.asList(new NormalizedPortfolioSizeAttribute(), new EntityTypeAttribute(), new NormalizedAssigneeNameAttribute(), new CompDBAssetsPurchasedAttribute(),new CompDBAssetsSoldAttribute(), new IsHumanAttribute(), new LatestExecutionDateAttribute(), new LatestAssigneeNameAttribute(), new PortfolioSizeAttribute(), new OrgRoleAttribute(), new CityAttribute(), new CountryAttribute(), new StateAttribute()));
        this.isObject=true;
    }
    
    @Override
    public String getName() {
        return Constants.LATEST_ASSIGNEE;
    }

}
