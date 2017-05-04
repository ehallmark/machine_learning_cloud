package model_testing;

import analysis.tech_tagger.TechTagger;
import tools.PortfolioList;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/4/2017.
 */
public class GatherTechnologyScorer {
    protected TechTagger model;
    public GatherTechnologyScorer(TechTagger model) {
        this.model=model;
    }

    public double accuracyOn(Map<String,Collection<String>> testSet,int numPredictions) {
        double averageAccuracy = testSet.entrySet().stream().collect(Collectors.averagingDouble(e->{
            String tech = e.getKey();
            Collection<String> assets = e.getValue();
            double accuracy = scoreAssets(model,tech,assets,numPredictions);
            System.out.println("Accuracy for "+tech+": "+accuracy);
            return accuracy;
        }));
        return averageAccuracy;
    }

    public static double scoreAssets(TechTagger model, String tech, Collection<String> assets, int numPredictions) {
       return ((double)(assets.stream().map(asset->{
           Collection<String> predictions = model.getTechnologiesFor(asset, PortfolioList.Type.patents,numPredictions).stream().map(pair->pair.getFirst()).collect(Collectors.toSet());
           System.out.println("Predictions for "+asset+" (Should be "+tech+" ): "+ String.join("; ",predictions));
           return predictions;
       }).filter(collection->collection.contains(tech)).count()))/assets.size();
    }
}
