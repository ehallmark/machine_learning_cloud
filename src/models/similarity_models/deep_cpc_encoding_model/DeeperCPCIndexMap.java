package models.similarity_models.deep_cpc_encoding_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.io.File;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/9/17.
 */
public class DeeperCPCIndexMap {
    private static final String CPC_TO_INDEX_FILENAME = Constants.DATA_FOLDER+"deeper_cpc_vae_cpc_to_idx_map.jobj";
    private static Map<String,Map<String,Integer>> depthCPCIndexCache = Collections.synchronizedMap(new HashMap<>());
    public static Map<String,Integer> loadOrCreateMapForDepth(RecursiveTask<CPCHierarchy> hierarchyTask, int depth, int maxNum) {
        if(!depthCPCIndexCache.containsKey(String.valueOf(depth)+"_"+String.valueOf(maxNum))) {
            // try loading from file
            Map<String,Integer> cpcIdxMap;
            try {
                cpcIdxMap = (Map<String,Integer>)Database.tryLoadObject(new File(CPC_TO_INDEX_FILENAME+depth+"_"+maxNum));
            } catch(Exception e) {
                cpcIdxMap = null;
            }
            if(cpcIdxMap==null) {
                CPCHierarchy hierarchy = hierarchyTask.invoke();

                Set<String> cpcs = Collections.synchronizedSet(new HashSet<>());
                int i = 0;
                Collection<CPC> nodes = hierarchy.getTopLevel();
                System.out.println("Nodes at start: "+nodes.size());
                while(i < depth) {
                    nodes.forEach(node->cpcs.add(node.getName()));
                    List<CPC> nodesCopy = new ArrayList<>(nodes);
                    nodes.clear();
                    nodesCopy.forEach(node->{
                        nodes.addAll(node.getChildren());
                    });
                    System.out.println("Nodes at iter "+i+": "+nodes.size());
                    i++;
                }

                AtomicInteger idx = new AtomicInteger(0);
                System.out.println("Could not find cpc idx map... creating new one now.");
                cpcIdxMap = cpcs.stream()
                        .sorted().sequential().collect(Collectors.toMap(e -> e, e -> idx.getAndIncrement()));
                System.out.println("Input size: " + cpcIdxMap.size());
                System.out.println("Saving cpc idx map...");
                Database.trySaveObject(cpcIdxMap,new File(CPC_TO_INDEX_FILENAME+depth+"_"+maxNum));
            }
            depthCPCIndexCache.put(String.valueOf(depth)+"_"+String.valueOf(maxNum),cpcIdxMap);
        }
        return depthCPCIndexCache.get(String.valueOf(depth)+"_"+String.valueOf(maxNum));
    }
}
