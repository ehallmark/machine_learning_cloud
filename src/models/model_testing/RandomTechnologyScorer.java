package models.model_testing;

import models.classification_models.ClassificationAttr;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/4/2017.
 */
public class RandomTechnologyScorer extends GatherTechnologyScorer {
    private static final Random rand = new Random(69);

    public RandomTechnologyScorer() {
        super(null);
    }

    public double accuracyOn(Map<String,Collection<String>> testSet,int numPredictions) {
        if(testSet.isEmpty()) return 0d;
        List<String> labels = new ArrayList<>(testSet.keySet());
        double averageAccuracy = testSet.entrySet().parallelStream().collect(Collectors.averagingDouble(e->{
            String tech = e.getKey();
            Collection<String> assets = e.getValue();
            double accuracy = scoreAssets(labels,tech,assets,numPredictions);
            return accuracy;
        }));
        return averageAccuracy;
    }

    public static double scoreAssets(List<String> labels, String tech, Collection<String> assets, int numPredictions) {
        if(assets.isEmpty()) return 0d;
        return ((double)(assets.stream().map(asset->{
            Collection<String> predictions = new ArrayList<>(numPredictions);
            for(int i = 0; i < numPredictions; i++) predictions.add(labels.get(rand.nextInt(labels.size())));
            if(predictions.isEmpty()) return Collections.emptyList();
            //System.out.println("Predictions for "+asset+" (Should be "+tech+" ): "+ String.join("; ",predictions));
            return predictions;
        }).filter(collection->collection.contains(tech)).count()))/assets.size();
    }

}
