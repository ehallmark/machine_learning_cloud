package models.value_models.bayesian.page_rank;

import models.value_models.regression.PageRankEvaluator;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/24/17.
 */
public class PageRankHelper {

    // run sim rank algorithm
    public static void main(String[] args) {
        long t1 = System.currentTimeMillis();
        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();

        Map<String,Collection<String>> citedPatentMap = Collections.synchronizedMap(new HashMap<>());
        Database.getPatentToCitedPatentsMap().entrySet().parallelStream().forEach(e->{
            String filing = assetToFilingMap.getPatentDataMap().get(e.getKey());
            if(filing!=null) {
                Collection<String> col = Collections.synchronizedCollection(e.getValue().stream().map(asset->{
                    return assetToFilingMap.getPatentDataMap().getOrDefault(asset,assetToFilingMap.getApplicationDataMap().getOrDefault(asset,asset));
                }).distinct().filter(asset->!asset.equals(filing)).collect(Collectors.toList()));
                if(!col.isEmpty()) {
                    citedPatentMap.put(filing,col);
                }
            }
        });


        PageRank algorithm = new PageRank(citedPatentMap,0.75);
        algorithm.solve(100);
        System.out.println("Finished algorithm");
        Map<String,Float> rankTable = algorithm.getRankTable();

        // update page rank evaluator
        PageRankEvaluator pageRankEvaluator = new PageRankEvaluator();
        Collection<String> patents = Database.getCopyOfAllPatents();
        Collection<String> applications = Database.getCopyOfAllApplications();

        System.out.println("Saving page rank evaluator...");
        pageRankEvaluator.setPatentDataMap(getDataMap(patents,rankTable,assetToFilingMap,true));
        pageRankEvaluator.setApplicationDataMap(getDataMap(applications,rankTable,assetToFilingMap,false));
        pageRankEvaluator.save();

        System.out.println("Rank Table size: "+rankTable.size());
        long t2 = System.currentTimeMillis();
        System.out.println("Time to complete: "+(t2-t1)/1000+" seconds");
    }

    private static Map<String,Number> getDataMap(Collection<String> assets, Map<String,Float> rankTable, AssetToFilingMap assetToFilingMap, boolean patents) {
        Map<String,Number> data = Collections.synchronizedMap(new HashMap<>());
        assets.parallelStream().forEach(asset->{
            String filing = (patents ? assetToFilingMap.getPatentDataMap() : assetToFilingMap.getApplicationDataMap()).get(asset);
            if(filing!=null) {
                Float rank = rankTable.get(asset);
                if (rank != null) {
                    data.put(asset, rank.doubleValue());
                }
            }
        });
        return data;
    }
}
