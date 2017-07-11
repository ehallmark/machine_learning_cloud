package seeding;

import models.model_testing.SplitModelData;

import java.util.*;

/**
 * Created by ehallmark on 5/1/17.
 */
public class PrintGatherTechnologies {
    public static void main(String[] args) {
        Collection<String> gatherTechnologies = SplitModelData.getBroadDataMap(SplitModelData.trainFile).keySet();
        System.out.println(String.join("\n",gatherTechnologies));
    }
}
