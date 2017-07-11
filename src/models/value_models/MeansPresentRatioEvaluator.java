package models.value_models;

import seeding.Database;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class MeansPresentRatioEvaluator extends ValueAttr {
    public MeansPresentRatioEvaluator() {
        super(ValueMapNormalizer.DistributionType.Uniform,"Means Present Value");
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        Map<String,Double> map = (Map<String,Double>)Database.tryLoadObject(new File("data/patent_to_means_present_ratio_map.jobj"));
        return Arrays.asList(map.entrySet().stream().collect(Collectors.toMap(e->e.getKey(),e->1d-e.getValue()))); // Inverse prob
    }
}
