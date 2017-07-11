package user_interface.ui_models.attributes.value;

import seeding.Constants;
import seeding.Database;
import tools.DateHelper;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 3/10/17.
 */
public class OverallEvaluator extends ValueAttr {
    private static final File mergedValueModelFile = new File("data/merged_value_model_map.jobj");

    public OverallEvaluator() {
        super(null, Constants.AI_VALUE);
        model=(Map<String,Double>)Database.tryLoadObject(mergedValueModelFile);
    }

    @Override
    public void setModel() {
        // do nothing
    }

    private static void runAndSaveOverallModel() {
        List<ValueAttr> evaluators = Arrays.asList(
                new CitationEvaluator(),
                new ClaimRatioEvaluator(),
                new PageRankEvaluator()
        );

        List<Double> weights = Arrays.asList(10d,9d,4d);

        Map<String,Double> mergedModel = new HashMap<>();

        Database.getValuablePatents().forEach(asset->{
            mergedModel.put(asset,average(asset,evaluators,weights));
        });
        Database.getExpiredPatentSet().forEach(asset->{
            mergedModel.put(asset,ValueMapNormalizer.DEFAULT_START);
        });
        Database.getLapsedPatentSet().forEach(asset->{
            mergedModel.put(asset,ValueMapNormalizer.DEFAULT_START);
        });

        DateHelper.addScoresToAssigneesFromPatents(Database.getAssignees(), mergedModel);

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

    @Override
    protected List<Map<String, Double>> loadModels() {
        return null;
    }


    public static void main(String[] args) {
        runAndSaveOverallModel();
    }
}
