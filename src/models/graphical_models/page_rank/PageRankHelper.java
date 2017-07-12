package models.graphical_models.page_rank;

import models.similarity_models.sim_rank.SimRankSimilarityModel;
import seeding.Database;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 4/24/17.
 */
public class PageRankHelper {
    public static final File file = new File("data/page_rank_table.jobj");

    // run sim rank algorithm
    public static void main(String[] args) {
        long t1 = System.currentTimeMillis();
        Map<String,Set<String>> citedPatentMap = new HashMap<>(Database.getPatentToCitedPatentsMap());
        citedPatentMap.putAll(Database.getAppToCitedPatentsMap());

        PageRank algorithm = new PageRank(citedPatentMap,0.75);
        algorithm.solve(100);
        algorithm.save(file);
        Map<String,Float> rankTable = new PageRank.Loader().loadRankTable(file);
        System.out.println("Rank Table size: "+rankTable.size());
        long t2 = System.currentTimeMillis();
        System.out.println("Time to complete: "+(t2-t1)/1000+" seconds");


    }
}
