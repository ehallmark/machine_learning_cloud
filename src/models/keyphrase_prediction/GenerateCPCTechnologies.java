package models.keyphrase_prediction;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import seeding.Constants;
import seeding.Database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/2/2017.
 */
public class GenerateCPCTechnologies {
    private static final File technologyMappingCSV = new File(Constants.DATA_FOLDER+"cpc_to_technology_mapping.csv");
    public static void main(String[] args) throws Exception {
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();

        Map<String,String> cpcToTitleMap = Database.getClassCodeToClassTitleMap();

        Collection<CPC> mainGroups = hierarchy.getLabelToCPCMap().entrySet().parallelStream()
                .filter(e->e.getValue().getNumParts()<=3)
                .map(e->e.getValue()).collect(Collectors.toList());

        System.out.println("Num main groups: "+mainGroups.size());

        mainGroups = mainGroups.parallelStream().filter(cpc->cpcToTitleMap.containsKey(cpc.getName()))
                .collect(Collectors.toList());

        System.out.println("Num main groups with valid title: "+mainGroups.size());

        BufferedWriter writer = new BufferedWriter(new FileWriter(technologyMappingCSV));
        mainGroups.stream().sorted(Comparator.comparing(e->e.getName())).forEach(cpc->{
            String text = String.valueOf(cpc.getNumParts())+","+cpc.getName()+","+cpcToTitleMap.get(cpc.getName());
            try {
                writer.write(text+"\n");
            }catch(Exception e) {
                e.printStackTrace();
            }
            System.out.println(text);
        });
        writer.flush();
        writer.close();
    }
}
