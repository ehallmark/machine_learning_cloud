package models.value_models.graphical.page_rank;

import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 4/24/17.
 */
public class SimRankHelper {
    public static final File similarityMapFile = new File("data/sim_rank_similarity_model");

    // run sim rank algorithm
    public static void main(String[] args) {
        Map<String,Collection<String>> citedPatentsMap = new HashMap<>(Database.getPatentToCitedPatentsMap());
        citedPatentsMap.putAll(Database.getAppToCitedPatentsMap());

        SimRank algorithm = new SimRank(citedPatentsMap, new ArrayList<>(citedPatentsMap.keySet()),0.75);
        int numEpochs = 50;
        for(int i = 0; i < numEpochs; i++) {
            long t1 = System.currentTimeMillis();
            algorithm.solve(1);
            long t2 = System.currentTimeMillis();
            System.out.println("Time to complete EPOCH "+i+": "+(t2-t1)/1000+" seconds");
            if(i%5==4) {
                saveRankTable(new File(similarityMapFile.getAbsolutePath()+"-epoch-"+i),citedPatentsMap,algorithm);
            }
        }
        saveRankTable(similarityMapFile,citedPatentsMap,algorithm);
    }


    private static void saveRankTable(File file, Map<String,Collection<String>> map, SimRank algorithm) {
        System.out.println("Starting to create similarity map!");
        Map<String, List<Pair<String, Float>>> similarityMap = Collections.synchronizedMap(new HashMap<>(map.size()));
        algorithm.rankTable.forEach((edge, sim) -> {
            if (!similarityMap.containsKey(edge.getNode1())) {
                similarityMap.put(edge.getNode1(), new ArrayList<>());
            }
            similarityMap.get(edge.getNode1()).add(new Pair<>(edge.getNode2(), sim));
            if (!similarityMap.containsKey(edge.getNode2())) {
                similarityMap.put(edge.getNode2(), new ArrayList<>());
            }
            similarityMap.get(edge.getNode2()).add(new Pair<>(edge.getNode1(), sim));
        });
        System.out.println("Sorting similarity map!");
        // sort
        similarityMap.entrySet().stream().parallel().forEach(e -> {
            List<Pair<String,Float>> list = e.getValue();
            Collections.sort(list, (p1, p2) -> p2.getSecond().compareTo(p1.getSecond()));
        });

        Database.trySaveObject(similarityMap, file);
        System.out.println("Finished similarity map!");
    }
}
