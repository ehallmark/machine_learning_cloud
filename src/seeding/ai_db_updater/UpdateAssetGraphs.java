package seeding.ai_db_updater;

import user_interface.ui_models.attributes.computable_attributes.asset_graphs.AssetGraph;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by ehallmark on 7/12/17.
 */
public class UpdateAssetGraphs {
    public static void main(String[] args) {
        Collection<AssetGraph> assetGraphs = Arrays.asList(

        );

        assetGraphs.forEach(graphAttr->{
            graphAttr.initAndSave();
        });
    }
}
