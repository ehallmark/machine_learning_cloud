package user_interface.ui_models.attributes.computable_attributes;

import models.value_models.ValueModelCombination;
import models.value_models.graphical.WIPOValueModel;
import models.value_models.regression.AIValueModel;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;

import java.util.Arrays;
import java.util.List;

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
    public String valueSuffix() {
        return "%";
    }

    @Override
    public Object missing() {
        return null;
    }

    @Override
    public List<Pair<Number, Number>> getRanges() {
        return Arrays.asList(
                new Pair<>(0,20),
                new Pair<>(20,40),
                new Pair<>(40,60),
                new Pair<>(60,80),
                new Pair<>(80,100)
        );
    }
}
