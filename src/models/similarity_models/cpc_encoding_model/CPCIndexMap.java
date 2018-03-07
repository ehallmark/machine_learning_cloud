package models.similarity_models.cpc_encoding_model;

import cpc_normalization.CPCHierarchy;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/9/17.
 */
public class CPCIndexMap {
    private static final String CPC_TO_INDEX_FILENAME = Constants.DATA_FOLDER+"cpc_vae_cpc_to_idx_map.jobj";
    private static Map<Integer,Map<String,Integer>> depthCPCIndexCache = Collections.synchronizedMap(new HashMap<>());
    public static Map<String,Integer> loadOrCreateMapForDepth(RecursiveTask<CPCHierarchy> hierarchyTask, int depth) {
        if(!depthCPCIndexCache.containsKey(depth)) {
            // try loading from file
            Map<String,Integer> cpcIdxMap;
            try {
                cpcIdxMap = (Map<String,Integer>)Database.tryLoadObject(new File(CPC_TO_INDEX_FILENAME+depth));
            } catch(Exception e) {
                cpcIdxMap = null;
            }
            if(cpcIdxMap==null) {
                AtomicInteger idx = new AtomicInteger(0);
                System.out.println("Could not find cpc idx map... creating new one now.");
                cpcIdxMap = hierarchyTask.invoke().getLabelToCPCMap().entrySet().stream().filter(e->e.getValue().getNumParts()<=depth)
                        .sorted(Comparator.comparing(e->e.getKey())).sequential().collect(Collectors.toMap(e -> e.getKey(), e -> idx.getAndIncrement()));
                System.out.println("Input size: " + cpcIdxMap.size());
                System.out.println("Saving cpc idx map...");
                Database.trySaveObject(cpcIdxMap,new File(CPC_TO_INDEX_FILENAME+depth));
            }
            depthCPCIndexCache.put(depth,cpcIdxMap);
        }
        return depthCPCIndexCache.get(depth);
    }
}
