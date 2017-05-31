package seeding;

import model_testing.SplitModelData;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.sql.SQLException;
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
