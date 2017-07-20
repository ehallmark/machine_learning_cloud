package seeding.ai_db_updater;

import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.*;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import user_interface.ui_models.portfolios.PortfolioList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateTermAdjustmentData {
    static Map<String,Integer> assetTermAdjustmentMap = new HashMap<>();

    public static void main(String[] args) {
        // Get term adjustment data
        TermAdjustmentHandler handler = new TermAdjustmentHandler();
        AtomicInteger cnt = new AtomicInteger(0);
        File dataFolder = new File("data/patent_term_adjustments/");
        Arrays.stream(dataFolder.listFiles()).forEach(file -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().parallel().forEach(line->{
                    if(cnt.getAndIncrement()%10000==0) System.out.println("Cnt: "+cnt.get());
                    handler.handleLine(line);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        handler.save();

        Map<String,Integer> appRefTermAdjustmentMap = handler.getPatentToTermAdjustmentMap();

        Map<String,String> patentToAppRefMap = (Map<String,String>) Database.tryLoadObject(Database.patentToAppRefMapFile);
        Map<String,String> appToAppRefMap = (Map<String,String>) Database.tryLoadObject(Database.appToAppRefMapFile);

        // Update patents and applications
        handleAssets(assetTermAdjustmentMap,Database.getCopyOfAllPatents(),patentToAppRefMap,appRefTermAdjustmentMap);
        handleAssets(assetTermAdjustmentMap,Database.getCopyOfAllApplications(),appToAppRefMap,appRefTermAdjustmentMap);


        Database.saveObject(assetTermAdjustmentMap, Database.updatedTermAdjustmentFile);
    }

    private static void handleAssets(Map<String,Integer> newMap, Collection<String> assets, Map<String,String> assetToAppRefMap, Map<String,Integer> termAdjustmentMap) {
        assets.forEach(asset->{
            String appRef = assetToAppRefMap.get(asset);
            if(appRef!=null) {
                Integer term = termAdjustmentMap.get(appRef);
                if(term!=null) {
                    newMap.put(asset,term);
                }
            }
        });
    }
}
