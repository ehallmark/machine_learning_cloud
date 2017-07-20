package seeding.ai_db_updater;

import seeding.Database;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 7/18/17.
 */
public class UpdateLifeRemainingMap {
    public static void main(String[] args) {
        UpdateTermAdjustmentData.main(args);
        Map<String,Integer> termAdjustmentMap = UpdateTermAdjustmentData.assetTermAdjustmentMap;

        Map<String,Integer> map = Collections.synchronizedMap(new HashMap<>());
        Database.getAllPatentsAndApplications().parallelStream().forEach(asset->{
            LocalDate priorityDate = Database.calculatePriorityDate(asset);
            if(termAdjustmentMap.containsKey(asset)) {
                priorityDate = priorityDate.plusDays(termAdjustmentMap.get(asset));
            }
            int lifeRemaining = Database.expirationDateFromPriorityDate(priorityDate);
            if(lifeRemaining>0) {
                map.put(asset,lifeRemaining);
            }
        });
        Database.saveObject(map,Database.lifeRemainingMapFile);
    }
}
