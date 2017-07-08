package ai_db_updater;

import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 7/8/2017.
 */
public class UpdateItemToIndexMap {
    private static final File itemToIndexMapFile = new File("data/item_to_index_map.jobj");
    private static final File indexToItemMapFile = new File("data/index_to_item_map.jobj");
    public static void main(String[] args) {
        Map<String,Integer> itemToIndexMap = new HashMap<>();
        Map<Integer,Collection<String>> indexToItemMap = new HashMap<>();
        Map<String,Collection<String>> relatedPatentsMap = Database.getPatentToRelatedPatentsMap();
        AtomicInteger cnt = new AtomicInteger(0);
        relatedPatentsMap.forEach((asset,related)->{
            if(itemToIndexMap.containsKey(asset)) {
                int idx = itemToIndexMap.get(asset);
                indexToItemMap.get(idx).addAll(related);
                related.forEach(rel->itemToIndexMap.put(rel,idx));
            } else {
                boolean found = false;
                for(String rel : related) {
                    if(itemToIndexMap.containsKey(rel)) {
                        int idx = itemToIndexMap.get(rel);
                        Collection<String> set = indexToItemMap.get(idx);
                        set.addAll(related);
                        set.add(asset);
                        set.forEach(r->itemToIndexMap.put(r,idx));
                        found=true;
                        break;
                    }
                }
                if(!found) {
                    // make new
                    Set<String> set = new HashSet<>();
                    set.add(asset);
                    set.addAll(related);
                    int idx = cnt.getAndIncrement();
                    indexToItemMap.put(idx,set);
                    set.forEach(rel->itemToIndexMap.put(rel,idx));
                }
            }
        });

        // save
        Database.trySaveObject(itemToIndexMap,itemToIndexMapFile);
        Database.trySaveObject(indexToItemMap,indexToItemMapFile);
    }
}
