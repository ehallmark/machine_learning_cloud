package seeding;

import seeding.ai_db_updater.UpdateAssetGraphs;

/**
 * Created by Evan on 12/16/2017.
 */
public class TestAssetGraphs {
    public static void main(String[] args) throws Exception {

        UpdateAssetGraphs.update(true);
    }
}
