package user_interface.ui_models.attributes.computable_attributes;

import models.value_models.ValueModelCombination;
import models.value_models.graphical.WIPOValueModel;
import models.value_models.regression.AIValueModel;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;

/**
 * Created by ehallmark on 3/10/17.
 */
public class OverallEvaluator extends ValueModelCombination implements RangeAttribute {

    public OverallEvaluator(boolean train) {
        super(Constants.AI_VALUE, new AIValueModel(),new WIPOValueModel(), train);
    }

    @Override
    public AbstractAttribute clone() {
        return new OverallEvaluator(train);
    }

    @Override
    public Number min() {
        return 0;
    }

    @Override
    public Number max() {
        return 100;
    }

    @Override
    public int nBins() {
        return 5;
    }

    @Override
    public String valueSuffix() {
        return "%";
    }

    @Override
    public Object missing() {
        return null;
    }
}
