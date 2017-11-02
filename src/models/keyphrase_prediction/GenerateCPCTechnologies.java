package models.keyphrase_prediction;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import seeding.Database;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/2/2017.
 */
public class GenerateCPCTechnologies {
    public static void main(String[] args) {
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();

        Map<String,String> cpcToTitleMap = Database.getClassCodeToClassTitleMap();

        Collection<CPC> mainGroups = hierarchy.getLabelToCPCMap().entrySet().parallelStream()
                .filter(e->e.getValue().getNumParts()==3)
                .map(e->e.getValue()).collect(Collectors.toList());

        System.out.println("Num main groups: "+mainGroups.size());

        mainGroups = mainGroups.parallelStream().filter(cpc->cpcToTitleMap.containsKey(cpc.getName()))
                .collect(Collectors.toList());

        System.out.println("Num main groups with valid title: "+mainGroups.size());

        mainGroups.forEach(cpc->{
            System.out.println(cpcToTitleMap.get(cpc.getName()));
        });
    }
}
