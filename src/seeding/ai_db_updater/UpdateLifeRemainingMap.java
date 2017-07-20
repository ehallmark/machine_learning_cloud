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

        Map<String,Integer> lifeRemainingMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,LocalDate> expirationDateMap = Collections.synchronizedMap(new HashMap<>());
        Database.getAllPatentsAndApplications().parallelStream().forEach(asset->{
            LocalDate priorityDate = Database.calculatePriorityDate(asset);
            if(termAdjustmentMap.containsKey(asset)) {
                priorityDate = priorityDate.plusDays(termAdjustmentMap.get(asset));
            }
            LocalDate expirationDate = priorityDate.plusYears(20);
            int lifeRemaining = Database.lifeRemainingFromPriorityDate(priorityDate);
            if(lifeRemaining>0) {
                lifeRemainingMap.put(asset,lifeRemaining);
                expirationDateMap.put(asset,expirationDate);
            }
        });
        Database.saveObject(lifeRemainingMap,Database.lifeRemainingMapFile);
        Database.saveObject(expirationDateMap,Database.expirationDateMapFile);
    }
}
