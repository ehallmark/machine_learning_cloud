package ui_models.attributes.value;

import seeding.Database;
import ui_models.attributes.ValueAttr;

import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class AssetsPurchasedEvaluator extends ValueAttr {
    public AssetsPurchasedEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal,"Assets Purchased Value");
        setModel();
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList(runModel());
    }

    private static Map<String,Double> runModel(){
        System.out.println("Starting to load citation evaluator...");
        Map<String,Integer> assigneeToAssetsPurchasedCountMap = Database.getAssigneeToAssetsPurchasedCountMap();
        Map<String,Double> model = new HashMap<>();
        System.out.println("Calculating scores for assignees...");
        assigneeToAssetsPurchasedCountMap.forEach((assignee,count)->{
            model.put(assignee,(double)count);
        });
        System.out.println("Finished evaluator...");
        return model;
    }
}
