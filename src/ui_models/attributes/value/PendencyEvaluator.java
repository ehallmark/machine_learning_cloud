package ui_models.attributes.value;

import seeding.Database;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 1/27/2017.
 */
public class PendencyEvaluator extends ValueAttr {
    public PendencyEvaluator() {
        super(ValueMapNormalizer.DistributionType.Uniform,"Pendency Value");
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList((Map<String,Double>)Database.tryLoadObject(ClaimEvaluator.pendencyModelFile));
    }

}
