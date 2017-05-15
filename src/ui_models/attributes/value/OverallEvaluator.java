package ui_models.attributes.value;

import seeding.Database;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 3/10/17.
 */
public class OverallEvaluator extends ValueAttr {
    private static final File mergedValueModelFile = new File("merged_value_model_map.jobj");

    public OverallEvaluator() {
        super(null, "AI Value");
        model=(Map<String,Double>)Database.tryLoadObject(mergedValueModelFile);
    }

    @Override
    public void setModel() {
        // do nothing
    }

    private static void runAndSaveOverallModel() {
        List<ValueAttr> evaluators = Arrays.asList(
                new CitationEvaluator(),
                new ClaimEvaluator(),
                //new AssetsSoldEvaluator(),
                //new AssetsPurchasedEvaluator(),
                new MarketEvaluator(),
                new TechnologyEvaluator()
        );

        List<Map<String,Double>> allModels = new ArrayList<>();
        evaluators.forEach(evaluator->{
           allModels.addAll(evaluator.loadModels());
        });

        Map<String,Double> mergedModel =new ValueMapNormalizer(ValueMapNormalizer.DistributionType.Normal).normalizeAndMergeModels(allModels);

        Database.trySaveObject(mergedModel,mergedValueModelFile);
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        return null;
    }


    public static void main(String[] args) {
        runAndSaveOverallModel();
    }
}
