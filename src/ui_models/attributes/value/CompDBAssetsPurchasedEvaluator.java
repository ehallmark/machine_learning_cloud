package ui_models.attributes.value;

import seeding.Database;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 1/27/2017.
 */
public class CompDBAssetsPurchasedEvaluator extends ValueAttr {
    public CompDBAssetsPurchasedEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal,"CompDB Assets Purchased Value");
        setModel();
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList(runModel());
    }

    private static Map<String,Double> runModel(){
        System.out.println("Starting to load CompDBAssetsPurchasedEvaluator evaluator...");
        Map<String,Integer> assigneeToAssetsPurchasedCountMap = Database.getCompDBAssigneeToAssetsPurchasedCountMap();
        Map<String,Double> model = new HashMap<>();
        System.out.println("Calculating scores for assignees...");
        assigneeToAssetsPurchasedCountMap.forEach((assignee,count)->{
            model.put(assignee,(double)count);
        });
        System.out.println("Finished evaluator...");
        return model;
    }
}
