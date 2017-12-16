package seeding;

import seeding.ai_db_updater.UpdateAssetGraphs;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;

/**
 * Created by Evan on 12/16/2017.
 */
public class TestFilingToAssetMap {
    public static void main(String[] args) throws Exception {

        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();

        System.out.println("Num patents to filings: "+assetToFilingMap.getPatentDataMap().size());
        System.out.println("Num applications to filings: "+assetToFilingMap.getApplicationDataMap().size());

        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();
        System.out.println("Num filings to patents: "+filingToAssetMap.getPatentDataMap().size());
        System.out.println("Num filings to applications: "+filingToAssetMap.getApplicationDataMap().size());

        
    }
}
