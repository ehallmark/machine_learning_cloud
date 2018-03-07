package cpc_normalization;

import seeding.Database;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/24/17.
 */
public class CPCCleaner {
    public static void main(String[] args) {
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();

        Collection<CPC> mainGroup = getCPCsAtDepth(hierarchy.getTopLevel(),3);
        Map<String,String> cpcToTitleMap = Database.getClassCodeToClassTitleMap();

        mainGroup.forEach(group->{
            System.out.println("Main group: "+group + ": "+cpcToTitleMap.get(group));
        });
        System.out.println("Num main groups: "+mainGroup.size());


    }

    public static Set<CPC> getCPCsAtDepth(Collection<CPC> startingNodes, int depth) {
        Set<CPC> cpcs = new HashSet<>();
        getCPCsAtDepthHelper(startingNodes,0,depth,cpcs);
        return cpcs;
    }

    private static void getCPCsAtDepthHelper(Collection<CPC> cpcs,int depth, int finalDepth, Collection<CPC> list) {
        cpcs.forEach(cpc-> {
            if (depth == finalDepth) {
                list.add(cpc);
            } else if (cpc.getChildren() != null) {
                getCPCsAtDepthHelper(cpc.getChildren(), depth + 1, finalDepth, list);
            }
        });
    }
}
