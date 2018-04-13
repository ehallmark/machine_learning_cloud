package models.value_models.regression;

import seeding.Constants;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.script_attributes.CountAttribute;

import java.util.Arrays;
import java.util.List;

/**
 * Created by ehallmark on 3/10/17.
 */
public class AIValueModel extends RegressionValueModel {
    private static final double INTERCEPT = -1.147;
    private static final List<Double> WEIGHTS = Arrays.asList(
            -0.5319562,
            0.0122155,
            0.0136566,
            0.0062181,
            -0.0056129,
            0.0057363
    );

    public static final List<AbstractAttribute> MODELS = Arrays.asList(
            new MeansPresentAttribute(),
            new CountAttribute(Constants.CLAIMS+Constants.COUNT_SUFFIX),
            new NumAssignmentsAttribute(),
            new CountAttribute(Constants.NUM_RELATED_ASSETS),
            new LengthOfSmallestIndependentClaimAttribute(),
            new CountAttribute(Constants.NUM_BACKWARD_CITATIONS)
    );

    public AIValueModel() {
        super(INTERCEPT, MODELS, WEIGHTS);
    }

    @Override
    public String getName() {
        return "aiValueRegressionModel";
    }

}
