package models.value_models;

import seeding.Database;
import user_interface.ui_models.attributes.ValueAttr;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 1/27/2017.
 */
public class PendencyEvaluator extends ValueAttr {
    public PendencyEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal,"Pendency Value");
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList((Map<String,Double>)Database.tryLoadObject(ClaimEvaluator.pendencyModelFile));
    }

}
