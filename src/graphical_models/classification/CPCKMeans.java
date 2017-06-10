package graphical_models.classification;

import ca.pjer.ekmeans.EKmeans;
import model_testing.GatherClassificationOptimizer;
import model_testing.SplitModelData;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/31/17.
 */
public class CPCKMeans {
    public static final int DEFAULT_CPC_DEPTH = 4;
    public static void main(String[] args) {
        Database.initializeDatabase();
        Map<String,Collection<String>> specificData = SplitModelData.getRawDataMap(SplitModelData.trainFile);
        Map<String,Collection<String>> broadData = SplitModelData.getBroadDataMap(SplitModelData.trainFile);

        List<String> specificTech = new ArrayList<>(specificData.keySet());
        List<String> broadTech = new ArrayList<>(broadData.keySet());

        // get patents
        List<String> patents = specificData.entrySet().stream().flatMap(e->e.getValue().stream()).distinct().filter(p->Database.classificationsFor(p).size()>0).collect(Collectors.toList());

        // get classifications
        int cpcDepth = DEFAULT_CPC_DEPTH;
        List<String> classifications = getClassifications(patents,cpcDepth);
        // create data
        int n = specificTech.size();
        double[][] points = new double[n][];
        for(int i = 0; i < n; i++) {
            points[i]=classVectorForPatents(specificData.get(specificTech.get(i)),classifications, DEFAULT_CPC_DEPTH);
        }

        // create centroids
        int k = broadData.size();
        double[][] centroids = new double[k][];
        for(int i = 0; i < k; i++) {
            centroids[i]=classVectorForPatents(broadData.get(broadTech.get(i)),classifications, DEFAULT_CPC_DEPTH);
        }

        EKmeans kmeans = new EKmeans(centroids,points);
        kmeans.setEqual(false);
        kmeans.run();

        int[] assignments = kmeans.getAssignments();

        Map<String,String> newTechMap = new HashMap<>(broadData.size());

        for(int i = 0; i < n; i++) {
            String specific = specificTech.get(i);
            String broad = broadTech.get(assignments[i]);
            newTechMap.put(specific,broad);
            System.out.println(specific+": "+broad);
        }

        GatherClassificationOptimizer.writeToCSV(newTechMap,new File("data/ai_grouped_gather_technologies.csv"));

    }

    public static double[] classVectorForPatents(Collection<String> patents, List<String> classifications, int cpcDepth) {
        double[] vec = new double[classifications.size()];
        Arrays.fill(vec, 0d);
        Collection<String> thisCPC = getClassifications(patents,cpcDepth);
        thisCPC.forEach(cpc -> {
            int idx = classifications.indexOf(cpc);
            if (idx >= 0) {
                vec[idx] += 1d / thisCPC.size();
            }
        });
        return vec;
    }

    public static List<String> getClassifications(Collection<String> patents, int cpcDepth) {
        return patents.stream().flatMap(p-> Database.classificationsFor(p).stream().map(cpc->cpc.substring(0,Math.min(cpcDepth,cpc.length())).trim())).distinct().collect(Collectors.toList());
    }
}
