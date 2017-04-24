package citation_graph;

import page_rank.SimRank;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 4/24/17.
 */
public class SimRankHelper {
    static File file = new File("sim_rank_table.jobj");

    // run sim rank algorithm
    public static void main(String[] args) {
        long t1 = System.currentTimeMillis();
        Map<String,Set<String>> patentToCitedPatentsMap = CitationPageRank.patentToCitedPatentsMap;
        SimRank algorithm = new SimRank(patentToCitedPatentsMap,0.75);
        algorithm.solve(10);
        algorithm.save(file);
        Map<String,Float> rankTable = SimRank.loadRankTable(file);
        System.out.println("Rank Table size: "+rankTable.size());
        long t2 = System.currentTimeMillis();
        System.out.println("Time to complete: "+(t2-t1)/1000+" seconds");
    }
}
