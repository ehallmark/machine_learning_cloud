package graphical_models.page_rank;

import model.edges.Edge;
import model.graphs.MarkovNet;
import similarity_models.sim_rank.CPCSimRankSimilarityModel;
import similarity_models.sim_rank.SimRankSimilarityModel;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 4/24/17.
 */
public class CPCSimRankHelper {
    public static final File file = new File("data/cpc_sim_rank_table19.jobj");

    // run sim rank algorithm
    public static void main(String[] args) {
        Map<String,Set<String>> classToPatentMap = CPCSimRankSimilarityModel.classificationToPatentMap;
        Map<String,Set<String>> patentToCitedPatentsMap = getPatentToCitedPatentsFromCPC(classToPatentMap);
        SimRank algorithm = new SimRank(patentToCitedPatentsMap,0.75, new MarkovNet()); // Markov Net to avoid directionality
        int numEpochs = 20;
        int previousEpoch = 0;
        File previousModelFile = new File("data/cpc_sim_rank_table"+previousEpoch+".jobj");
        Map<Edge<String>,Float> previousRankTable = previousModelFile.exists() ? new SimRank.Loader().loadRankTable(previousModelFile) : Collections.synchronizedMap(new HashMap<>());
        algorithm.rankTable=previousRankTable;
        for(int i = previousEpoch+1; i < numEpochs; i++) {
            long t1 = System.currentTimeMillis();
            algorithm.solve(1);
            algorithm.save(new File("data/cpc_sim_rank_table"+i+".jobj"));
            long t2 = System.currentTimeMillis();
            System.out.println("Time to complete: "+(t2-t1)/1000+" seconds");
        }
        //System.out.println("Rank Table size: "+rankTable.size());
    }

    private static Map<String,Set<String>> getPatentToCitedPatentsFromCPC(Map<String,Set<String>> classToPatentsMap) {
        System.out.println("Converting class to patent map into patent to patent citations...");
        Map<String,Set<String>> patentToCitedPatents = new HashMap<>();
        classToPatentsMap.forEach((cpc,patents)->{
            List<String> patentList = new ArrayList<>(patents);
            for(int i = 0; i < patentList.size(); i++) {
                String patent1 = patentList.get(i);
                Set<String> pSet;
                if(patentToCitedPatents.containsKey(patent1)) {
                    pSet = patentToCitedPatents.get(patent1);
                } else {
                    pSet = new HashSet<>();
                    patentToCitedPatents.put(patent1,pSet);
                }
                for(int j = i+1; j < patentList.size(); j++) {
                    String patent2 = patentList.get(j);
                    pSet.add(patent2);
                }
            }
        });
        System.out.println("Finished.");
        return patentToCitedPatents;
    }
}
