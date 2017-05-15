package ui_models.attributes.value;

import seeding.Database;

import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class AssetsSoldEvaluator extends ValueAttr {
    public AssetsSoldEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal,"Assets Sold Value");
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList(runModel());
    }

    private static Map<String,Double> runModel(){
        System.out.println("Starting to load citation evaluator...");
        Map<String,Integer> assigneeToAssetsSoldCountMap = Database.getAssigneeToAssetsSoldCountMap();
        Map<String,Double> model = new HashMap<>();
        System.out.println("Calculating scores for assignees...");
        assigneeToAssetsSoldCountMap.forEach((assignee,count)->{
            model.put(assignee,(double)count);
        });
        System.out.println("Finished evaluator...");
        return model;
    }
}
