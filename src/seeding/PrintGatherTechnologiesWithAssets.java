package seeding;

import model_testing.SplitModelData;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/1/17.
 */
public class PrintGatherTechnologiesWithAssets {
    public static void main(String[] args) {
        Map<String,Collection<String>> gatherTechnologies = SplitModelData.getGatherTechnologyTrainingDataMap();
        System.out.println(String.join("\n",gatherTechnologies.entrySet().stream().flatMap(e->e.getValue().stream().map(val->"\""+e.getKey()+"\","+val)).collect(Collectors.toList())));
    }
}
