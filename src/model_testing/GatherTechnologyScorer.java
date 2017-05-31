package model_testing;

import ui_models.attributes.classification.ClassificationAttr;
import ui_models.portfolios.PortfolioList;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/4/2017.
 */
public class GatherTechnologyScorer {
    protected ClassificationAttr model;
    public GatherTechnologyScorer(ClassificationAttr model) {
        this.model=model;
    }

    public double accuracyOn(Map<String,Collection<String>> testSet,int numPredictions) {
        if(testSet.isEmpty()) return 0d;
        double averageAccuracy = testSet.entrySet().stream().collect(Collectors.averagingDouble(e->{
            String tech = e.getKey();
            Collection<String> assets = e.getValue();
            double accuracy = scoreAssets(model,tech,assets,numPredictions);
            return accuracy;
        }));
        return averageAccuracy;
    }

    public static double scoreAssets(ClassificationAttr model, String tech, Collection<String> assets, int numPredictions) {
        if(assets.isEmpty()) return 0d;
        return ((double)(assets.stream().map(asset->{
            Collection<String> predictions = model.attributesFor(PortfolioList.asList(asset, PortfolioList.Type.patents),numPredictions).stream().map(pair->pair.getFirst()).collect(Collectors.toSet());
            if(predictions==null||predictions.isEmpty()) return Collections.emptyList();
            //System.out.println("Predictions for "+asset+" (Should be "+tech+" ): "+ String.join("; ",predictions));
            return predictions;
        }).filter(collection->collection.contains(tech)).count()))/assets.size();
    }
}
