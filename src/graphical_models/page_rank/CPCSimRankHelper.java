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
        Map<String,Set<String>> cpcToPatentsMap = CPCSimRankSimilarityModel.classificationToPatentMap;
        SimRank algorithm = new SimRank(cpcToPatentsMap,0.75);
        int numEpochs = 20;
        int previousEpoch = 0;
        File previousModelFile = new File("data/cpc_sim_rank_table"+previousEpoch+".jobj");
        Map<Edge<String>,Float> previousRankTable = Collections.synchronizedMap(previousModelFile.exists() ? new SimRank.Loader().loadRankTable(previousModelFile) : new HashMap<>());
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
}
