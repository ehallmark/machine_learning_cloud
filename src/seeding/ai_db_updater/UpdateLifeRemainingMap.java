package seeding.ai_db_updater;

import seeding.Database;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 7/18/17.
 */
public class UpdateLifeRemainingMap {
    public static void main(String[] args) {
        Map<String,Integer> map = Collections.synchronizedMap(new HashMap<>());
        Database.getAllPatentsAndApplications().parallelStream().forEach(asset->{
            int lifeRemaining = Database.calculateLifeRemaining(asset);
            if(lifeRemaining>0) {
                map.put(asset,lifeRemaining);
            }
        });
        Database.saveObject(map,Database.lifeRemainingMapFile);
    }
}
