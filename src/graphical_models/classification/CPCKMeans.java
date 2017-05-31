package graphical_models.classification;

import ca.pjer.ekmeans.EKmeans;
import model_testing.SplitModelData;
import model_testing.TrainModelsWithLatestGatherData;
import seeding.Database;

import java.io.File;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/31/17.
 */
public class CPCKMeans {
    public static void main(String[] args) {
        Database.initializeDatabase();
        Map<String,Collection<String>> specificData = SplitModelData.getRawDataMap(SplitModelData.trainFile);
        Map<String,Collection<String>> broadData = SplitModelData.getBroadDataMap(SplitModelData.trainFile);

        List<String> specificTech = new ArrayList<>(specificData.keySet());
        List<String> broadTech = new ArrayList<>(broadData.keySet());

        // get patents
        List<String> patents = specificData.entrySet().stream().flatMap(e->e.getValue().stream()).distinct().filter(p->Database.classificationsFor(p).size()>0).collect(Collectors.toList());

        // get classifications
        List<String> classifications = patents.stream().flatMap(p-> Database.classificationsFor(p).stream()).distinct().collect(Collectors.toList());

        // create data
        int n = specificTech.size();
        double[][] points = new double[n][];
        for(int i = 0; i < n; i++) {
            points[i]=vectorForPatents(specificData.get(specificTech.get(i)),classifications);
        }

        // create centroids
        int k = broadData.size();
        double[][] centroids = new double[k][];
        for(int i = 0; i < k; i++) {
            centroids[i]=vectorForPatents(broadData.get(broadTech.get(i)),classifications);
        }

        EKmeans kmeans = new EKmeans(centroids,points);
        kmeans.run();

        int[] assignments = kmeans.getAssignments();

        Map<String,String> newTechMap = new HashMap<>(broadData.size());

        for(int i = 0; i < n; i++) {
            newTechMap.put(specificTech.get(i),broadTech.get(assignments[i]));
        }

        TrainModelsWithLatestGatherData.writeToCSV(newTechMap,new File("data/ai_grouped_gather_technologies.csv"));

    }


    public static double[] vectorForPatents(Collection<String> patents, List<String> classifications) {
        double[] vec = new double[classifications.size()];
        Arrays.fill(vec,0d);
        Collection<String> thisCPC = patents.stream().flatMap(patent->Database.classificationsFor(patent).stream()).collect(Collectors.toList());
        thisCPC.forEach(cpc->{
           vec[classifications.indexOf(cpc)]+=1d/thisCPC.size();
        });
        return vec;
    }
}
