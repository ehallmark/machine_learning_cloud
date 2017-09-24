package user_interface.ui_models.attributes.computable_attributes.asset_graphs;

import seeding.Constants;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCitedAssetsMap;

/**
 * Created by Evan on 9/24/2017.
 */
public class BackwardCitationAttribute extends AssetGraph {
    public BackwardCitationAttribute() {
        super(true,new AssetToCitedAssetsMap());
    }

    @Override
    public String getName() {
        return Constants.BACKWARD_CITATION;
    }

    @Override
    public String getType() {
        return "keyword";
    }
}
