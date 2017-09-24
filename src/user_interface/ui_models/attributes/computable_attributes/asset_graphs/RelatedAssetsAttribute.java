package user_interface.ui_models.attributes.computable_attributes.asset_graphs;

import seeding.Constants;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCitedAssetsMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToRelatedAssetsMap;

/**
 * Created by Evan on 9/24/2017.
 */
public class RelatedAssetsAttribute extends AssetGraph {
    public RelatedAssetsAttribute() {
        super(false,new AssetToRelatedAssetsMap());
    }

    @Override
    public String getName() {
        return Constants.ALL_RELATED_ASSETS;
    }

    @Override
    public String getType() {
        return "keyword";
    }
}
