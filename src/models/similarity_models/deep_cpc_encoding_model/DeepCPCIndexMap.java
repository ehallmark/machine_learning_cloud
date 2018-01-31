package models.similarity_models.deep_cpc_encoding_model;

import cpc_normalization.CPCHierarchy;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.io.File;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/9/17.
 */
public class DeepCPCIndexMap {
    private static final String CPC_TO_INDEX_FILENAME = Constants.DATA_FOLDER+"deep_cpc_vae_cpc_to_idx_map.jobj";
    private static Map<Integer,Map<String,Integer>> depthCPCIndexCache = Collections.synchronizedMap(new HashMap<>());
    public static Map<String,Integer> loadOrCreateMapForDepth(RecursiveTask<CPCHierarchy> hierarchyTask, int depth, int minOccurrences) {
        if(!depthCPCIndexCache.containsKey(depth)) {
            // try loading from file
            Map<String,Integer> cpcIdxMap;
            try {
                cpcIdxMap = (Map<String,Integer>)Database.tryLoadObject(new File(CPC_TO_INDEX_FILENAME+depth+"_"+minOccurrences));
            } catch(Exception e) {
                cpcIdxMap = null;
            }
            if(cpcIdxMap==null) {
                Map<String,Set<String>> appToCPCStringMap = Collections.synchronizedMap(new HashMap<>(new AssetToCPCMap().getApplicationDataMap()));
                // limit cpcs based on frequency
                Set<String> prevalentCPCs = appToCPCStringMap.entrySet().parallelStream()
                        .flatMap(e->e.getValue().stream().map(cpc-> ClassCodeHandler.convertToLabelFormat(cpc)))
                        .collect(Collectors.groupingBy(cpc->cpc,Collectors.counting()))
                        .entrySet().parallelStream()
                        .filter(e->e.getValue()>=minOccurrences)
                        .map(e->e.getKey()).collect(Collectors.toSet());
                System.out.println("Num prevalent cpcs: "+prevalentCPCs.size());

                AtomicInteger idx = new AtomicInteger(0);
                System.out.println("Could not find cpc idx map... creating new one now.");
                cpcIdxMap = hierarchyTask.invoke().getLabelToCPCMap().entrySet().stream().filter(e->e.getValue().getNumParts()<=depth)
                        .filter(e->prevalentCPCs.contains(e.getKey()))
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
