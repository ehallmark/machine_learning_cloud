package seeding.ai_db_updater;

import user_interface.ui_models.attributes.computable_attributes.asset_graphs.AssetGraph;
import user_interface.ui_models.attributes.computable_attributes.asset_graphs.BackwardCitationAttribute;
import user_interface.ui_models.attributes.computable_attributes.asset_graphs.RelatedAssetsAttribute;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by ehallmark on 7/12/17.
 */
public class UpdateAssetGraphs {
    public static void update(boolean test) {
        Collection<AssetGraph> assetGraphs = Arrays.asList(
                new RelatedAssetsAttribute(),
                new BackwardCitationAttribute()
        );

        assetGraphs.forEach(graphAttr->{
            graphAttr.initAndSave(test);
        });
    }
}
