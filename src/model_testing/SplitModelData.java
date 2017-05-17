package model_testing;

import org.datavec.api.berkeley.Triple;
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
    public static final File file = new File("Gather Technologies- Map to Broader Tag.xls");
    public static final Random rand = new Random(69);
    public static final File trainFile = new File("data/gather_tech_train_map.jobj");
    public static final File testFile = new File("data/gather_tech_test_map.jobj");
    public static final File validationFile = new File("data/gather_tech_validation_map.jobj");

    static {
        try {
            gatherToBroadTechMap=GetEtsiPatentsList.getExcelLists(file, 13, 1, 2).stream().filter(r->r.size()>=2)
                    .collect(Collectors.toMap(list->list.get(0),list->list.get(1)));

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

    public static Map<String,Collection<String>> regroupData(Map<String,Collection<String>> data) {
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

    public static void splitData(Map<String,Collection<String>> data) {
        Map<String,Collection<String>> train = new HashMap<>();
        Map<String,Collection<String>> test = new HashMap<>();
        Map<String,Collection<String>> val = new HashMap<>();
        int maxTechSize = 50;
        data = regroupData(data);
        data.forEach((tech,assets)->{
            assets = assets.stream().filter(p->(p.length()==7||p.length()==8)&&isNumber(p.substring(2))).collect(Collectors.toList());
            if(assets.size()<5) return;
            Set<String> trainSet = new HashSet<>();
            Set<String> testSet = new HashSet<>();
            Set<String> valSet = new HashSet<>();
            List<String> assetList = new ArrayList<>(assets);
            Collections.shuffle(assetList,rand);
            int size = Math.min(assetList.size(),maxTechSize);
            trainSet.addAll(assetList.subList(0,Math.round(0.7f*size)));
            testSet.addAll(assetList.subList(Math.round(0.7f*size),Math.round(0.85f*size)));
            valSet.addAll(assetList.subList(Math.round(0.85f*size),size));
            train.put(tech,trainSet);
            test.put(tech,testSet);
            val.put(tech,valSet);
            System.out.println("Finished tech: "+tech);
        });

        Database.trySaveObject(train,trainFile);
        Database.trySaveObject(test,testFile);
        Database.trySaveObject(val,validationFile);
        System.out.println("Num Technologies: "+train.size());
    }

    public static Map<String,Collection<String>> getGatherTechnologyTrainingDataMap() {
        return (Map<String,Collection<String>>)Database.tryLoadObject(trainFile);
    }


    public static Map<String,Collection<String>> getGatherTechnologyTestDataMap() {
        return (Map<String,Collection<String>>)Database.tryLoadObject(testFile);
    }


    public static Map<String,Collection<String>> getGatherTechnologyValidationDataMap() {
        return (Map<String,Collection<String>>)Database.tryLoadObject(validationFile);
    }

    public static void main(String[] args) {
        Map<String,Collection<String>> gatherTechMap = Database.getGatherTechMap();
        splitData(gatherTechMap);
    }


}
