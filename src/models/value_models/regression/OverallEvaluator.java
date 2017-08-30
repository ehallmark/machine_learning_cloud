package models.value_models.regression;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 3/10/17.
 */
public class OverallEvaluator extends ValueAttr {
    private static final File mergedValueModelFile = new File("data/merged_value_model_map.jobj");
    private static Map<String,Double> aiValueMap;

    @Override
    public String getName() {
        return Constants.AI_VALUE;
    }

    @Override
    public Double attributesFor(Collection<String> portfolio, int limit) {
        synchronized (OverallEvaluator.class) {
            if(aiValueMap==null) {
                aiValueMap = (Map<String,Double>)Database.tryLoadObject(mergedValueModelFile);
            }
        }
        return portfolio.stream().map(item->{
            return aiValueMap.getOrDefault(item,null);
        }).filter(item->item!=null).findAny().orElse(null);
    }


    private static void runAndSaveOverallModel() {
        List<ValueAttr> evaluators = Arrays.asList(
                new CitationEvaluator(),
                new ClaimEvaluator(),
                new PageRankEvaluator(),
                new MeansPresentEvaluator()
        );

        List<Double> weights = Arrays.asList(10d,9d,4d);

        Map<String,Double> mergedModel = new HashMap<>();

        Database.getCopyOfAllPatents().parallelStream().forEach(asset->{
            mergedModel.put(asset,average(asset,evaluators,weights));
        });
        Database.getCopyOfAllApplications().parallelStream().forEach(asset->{
            mergedModel.put(asset,average(asset,evaluators,weights));
        });

        Database.trySaveObject(mergedModel,mergedValueModelFile);
    }

    private static double average(String item, List<ValueAttr> values, List<Double> weights) {
        if(values.size()!=weights.size()) throw new RuntimeException("Invalid weights size");
        double weightSum = weights.stream().collect(Collectors.summingDouble(d->d));
        double score = 0d;
        for(int i = 0; i < values.size(); i++) {
            score += (values.get(i).evaluate(item)) * (weights.get(i)/weightSum);
        }
        return score;
    }


    public static void main(String[] args) {
        runAndSaveOverallModel();
    }
}
