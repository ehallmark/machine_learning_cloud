package value_estimation;

import analysis.tech_tagger.TechTagger;

import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 2/25/2017.
 */
public class SpecificTechnologyEvaluator extends Evaluator {
    private static final String MODEL_PREFIX = "Value in ";
    private TechTagger tagger;
    private String technology;
    public SpecificTechnologyEvaluator(ValueMapNormalizer.DistributionType distributionType, String technology, TechTagger tagger) {
        super(distributionType,MODEL_PREFIX+technology);
        this.tagger=tagger;
        this.technology=technology;
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        throw new RuntimeException("Model does not need to be loaded...");
    }

    @Override
    public Map<String,Double> getMap() {
        throw new RuntimeException("There is no model map...");
    }

    @Override
    public double evaluate(String token) {
        return tagger.getTechnologyValueFor(token,technology);
    }
}
