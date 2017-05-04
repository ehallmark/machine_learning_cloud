package model_testing;

import org.datavec.api.berkeley.Triple;
import seeding.Database;

import java.io.File;
import java.util.*;

/**
 * Created by Evan on 5/4/2017.
 */
public class SplitModelData {
    public static final Random rand = new Random(69);
    public static final File trainFile = new File("data/gather_tech_train_map.jobj");
    public static final File testFile = new File("data/gather_tech_test_map.jobj");
    public static final File validationFile = new File("data/gather_tech_validation_map.jobj");

    public static void splitData(Map<String,Collection<String>> data) {
        Map<String,Collection<String>> train = new HashMap<>();
        Map<String,Collection<String>> test = new HashMap<>();
        Map<String,Collection<String>> val = new HashMap<>();
        data.forEach((tech,assets)->{
            if(assets.size()<20) return;
            Set<String> trainSet = new HashSet<>();
            Set<String> testSet = new HashSet<>();
            Set<String> valSet = new HashSet<>();
            List<String> assetList = new ArrayList<>(assets);
            Collections.shuffle(assetList,rand);
            int size = assetList.size();
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

    public static void main(String[] args) {
        Map<String,Collection<String>> gatherTechMap = Database.getGatherTechMap();
        splitData(gatherTechMap);
    }
}
