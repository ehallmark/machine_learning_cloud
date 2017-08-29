package models.graphical_models.page_rank;

import models.value_models.PageRankEvaluator;
import seeding.Database;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 4/24/17.
 */
public class PageRankHelper {
    public static final File file = new File("data/page_rank_table.jobj");

    // run sim rank algorithm
    public static void main(String[] args) {
        long t1 = System.currentTimeMillis();
        Map<String,Collection<String>> citedPatentMap = new HashMap<>(Database.getPatentToCitedPatentsMap());
        citedPatentMap.putAll(Database.getAppToCitedPatentsMap());

        PageRank algorithm = new PageRank(citedPatentMap,0.75);
        algorithm.solve(100);
        System.out.println("Finished algorithm");
        Map<String,Float> rankTable = algorithm.getRankTable();

        // update page rank evaluator
        PageRankEvaluator pageRankEvaluator = new PageRankEvaluator();
        Collection<String> patents = Database.getCopyOfAllPatents();
        Collection<String> applications = Database.getCopyOfAllApplications();

        System.out.println("Saving page rank evaluator...");
        pageRankEvaluator.setPatentDataMap(getDataMap(patents,rankTable));
        pageRankEvaluator.setApplicationDataMap(getDataMap(applications,rankTable));
        pageRankEvaluator.save();

        System.out.println("Rank Table size: "+rankTable.size());
        long t2 = System.currentTimeMillis();
        System.out.println("Time to complete: "+(t2-t1)/1000+" seconds");
    }

    private static Map<String,Number> getDataMap(Collection<String> assets, Map<String,Float> rankTable) {
        Map<String,Number> data = Collections.synchronizedMap(new HashMap<>());
        assets.parallelStream().forEach(asset->{
            Float rank = rankTable.get(asset);
            if(rank != null) {
                data.put(asset,rank.doubleValue());
            }
        });
        return data;
    }
}
