package ui_models.attributes.value;

import seeding.Constants;
import seeding.Database;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 1/27/2017.
 */
public class CompDBAssetsSoldEvaluator extends ValueAttr {
    public CompDBAssetsSoldEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal, Constants.COMPDB_ASSETS_SOLD_VALUE, ValueMapNormalizer.DEFAULT_START);
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList(runModel());
    }

    private static Map<String,Double> runModel(){
        System.out.println("Starting to load CompDBAssetsSoldEvaluator evaluator...");
        Map<String,Integer> assigneeToAssetsSoldCountMap = Database.getCompDBAssigneeToAssetsSoldCountMap();
        Map<String,Double> model = new HashMap<>();
        System.out.println("Calculating scores for assignees...");
        assigneeToAssetsSoldCountMap.forEach((assignee,count)->{
            model.put(assignee,(double)count);
        });
        System.out.println("Finished evaluator...");
        return model;
    }
}
