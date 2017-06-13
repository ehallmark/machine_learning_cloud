package graphical_models.page_rank;

import model.edges.Edge;
import seeding.Database;
import similarity_models.sim_rank.SimRankSimilarityModel;
import util.Pair;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 4/24/17.
 */
public class SimRankHelper {
    public static final File similarityMapFile = new File("data/sim_rank_similarity_map.jobj");

    // run sim rank algorithm
    public static void main(String[] args) {
        Map<String,Set<String>> patentToCitedPatentsMap = SimRankSimilarityModel.getPatentToCitedPatentsMap();
        SimRank algorithm = new SimRank(patentToCitedPatentsMap, new ArrayList<>(patentToCitedPatentsMap.keySet()),0.75);
        int numEpochs = 20;
        Map<Edge<String>,Float> previousRankTable = new HashMap<>();
        algorithm.rankTable=previousRankTable;

        for(int i = 0; i < numEpochs; i++) {
            long t1 = System.currentTimeMillis();
            algorithm.solve(1);
            long t2 = System.currentTimeMillis();
            System.out.println("Time to complete: "+(t2-t1)/1000+" seconds");

            System.out.println("Starting to create similarity map!");
            Map<String,List<Pair<String,Float>>> similarityMap = new HashMap<>(patentToCitedPatentsMap.size());
            algorithm.rankTable.forEach((edge,sim)->{
                if(!similarityMap.containsKey(edge.getNode1())) {
                    similarityMap.put(edge.getNode1(),new ArrayList<>());
                }
                similarityMap.get(edge.getNode1()).add(new Pair<>(edge.getNode2(),sim));
                if(!similarityMap.containsKey(edge.getNode2())) {
                    similarityMap.put(edge.getNode2(),new ArrayList<>());
                }
                similarityMap.get(edge.getNode2()).add(new Pair<>(edge.getNode1(),sim));
            });
            System.out.println("Sorting similarity map!");
            // sort
            similarityMap.forEach((k,list)->{
                Collections.sort(list,(p1,p2)->p2._2.compareTo(p1._2));
            });

            Database.trySaveObject(similarityMap,similarityMapFile);
            System.out.println("Finished similarity map!");
        }
    }
}
