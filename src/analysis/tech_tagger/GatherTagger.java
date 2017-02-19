package analysis.tech_tagger;

import com.googlecode.concurrenttrees.radix.RadixTree;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import tools.MinHeap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 2/18/2017.
 */
public class GatherTagger {
    private static Map<String,String> technologyMap;
    static {
        technologyMap=(Map<String,String>)Database.tryLoadObject(BuildCPCToGatherStatistics.techMapFile);
    }

    public String getTechnologyFor(String patent) {
         if(technologyMap.containsKey(patent)) {
             return technologyMap.get(patent);
         } else {
             return "";
         }
    }

}
