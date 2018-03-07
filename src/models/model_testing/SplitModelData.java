package models.model_testing;

import seeding.Database;
import seeding.GetEtsiPatentsList;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/4/2017.
 */
public class SplitModelData {
    public static Map<String,String> gatherToBroadTechMap;
    public static final File excelFile = new File("Gather Technologies- Map to Broader Tag.xls");
    public static final Random rand = new Random(69);
    public static final File trainFile = new File("data/gather_tech_train_map.jobj");
    public static final File testFile = new File("data/gather_tech_test_map.jobj");
    public static final File validation1File = new File("data/gather_tech_validation1_map.jobj");
    public static final File validation2File = new File("data/gather_tech_validation2_map.jobj");
    public static final File broadTechMapFile = new File("data/broad_tech_map.jobj");
    static {
        try {
            if(broadTechMapFile.exists()) {
                gatherToBroadTechMap=(Map<String,String>)Database.tryLoadObject(broadTechMapFile);
            } else {
                gatherToBroadTechMap = GetEtsiPatentsList.getExcelLists(excelFile, 13, 1, 2).stream().filter(r -> r.size() >= 2)
                        .collect(Collectors.toMap(list -> list.get(0), list -> list.get(1)));
                saveBroadTechMap(gatherToBroadTechMap);
            }

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Unable to create broad tag map");
        }
    }

    public static boolean isNumber(String s) {
        if(s==null)return false;
        try {
            Integer.valueOf(s);
            return true;
        } catch(Exception nfe) {
            return false;
        }
    }

    public static void saveBroadTechMap(Map<String,String> broadTechMap) {
        Database.trySaveObject(broadTechMap,broadTechMapFile);
    }

    public static Map<String,Collection<String>> regroupData(Map<String,Collection<String>> data, Map<String,String> gatherToBroadTechMap) {
        Map<String,Collection<String>> grouped = new HashMap<>();
        data.forEach((tech,assets)->{
            if(gatherToBroadTechMap.containsKey(tech)) {
                String broad = gatherToBroadTechMap.get(tech);
                if(grouped.containsKey(broad)) {
                    grouped.get(broad).addAll(assets);
                } else {
                    grouped.put(broad,new HashSet<>(assets));
                }
            }
        });
        return grouped;
    }

    public static Map<String,Collection<String>> regroupData(Map<String,Collection<String>> data) {
        return regroupData(data,gatherToBroadTechMap);
    }

    public static void splitData(Map<String,Collection<String>> data) {
        Map<String,Collection<String>> train = new HashMap<>();
        Map<String,Collection<String>> test = new HashMap<>();
        Map<String,Collection<String>> val1 = new HashMap<>();
        Map<String,Collection<String>> val2 = new HashMap<>();
        //data = regroupData(data);
        data.forEach((tech,assets)->{
            assets = assets.stream().filter(p->(p.length()==7||p.length()==8)&&isNumber(p.substring(2))).collect(Collectors.toList());
            if(assets.size()<5) return;
            Set<String> trainSet = new HashSet<>();
            Set<String> testSet = new HashSet<>();
            Set<String> val1Set = new HashSet<>();
            Set<String> val2Set = new HashSet<>();
            List<String> assetList = new ArrayList<>(assets);
            Collections.shuffle(assetList,rand);
            int size = assetList.size();
            trainSet.addAll(assetList.subList(0,Math.round(0.65f*size)));
            testSet.addAll(assetList.subList(Math.round(0.65f*size),Math.round(0.70f*size)));
            val1Set.addAll(assetList.subList(Math.round(0.70f*size),Math.round(0.85f*size)));
            val2Set.addAll(assetList.subList(Math.round(0.85f*size),size));
            train.put(tech,trainSet);
            test.put(tech,testSet);
            val1.put(tech,val1Set);
            val2.put(tech,val2Set);
            System.out.println("Finished tech: "+tech);
        });

        Database.trySaveObject(train,trainFile);
        Database.trySaveObject(test,testFile);
        Database.trySaveObject(val1,validation1File);
        Database.trySaveObject(val2,validation2File);
        System.out.println("Num Technologies: "+train.size());
    }

    public static Map<String,Collection<String>> getBroadDataMap(File file) {
        return regroupData((Map<String,Collection<String>>)Database.tryLoadObject(file));
    }


    public static Map<String,Collection<String>> getRawDataMap(File file) {
        return (Map<String,Collection<String>>)Database.tryLoadObject(file);
    }

    public static void main(String[] args) {
        Map<String,Collection<String>> gatherTechMap = Database.getGatherTechnologyToPatentMap();
        splitData(gatherTechMap);
    }

}
