package ui_models.attributes.value;

import seeding.Database;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 1/27/2017.
 */
public class ClassEvaluator extends ValueAttr {
    public ClassEvaluator() {
        super(ValueMapNormalizer.DistributionType.None,"Classification Value");
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList((Map<String,Double>)Database.tryLoadObject(TechnologyEvaluator.file));
    }

}
