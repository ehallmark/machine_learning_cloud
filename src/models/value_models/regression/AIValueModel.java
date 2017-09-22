package models.value_models.regression;

import seeding.Constants;
import user_interface.ui_models.attributes.*;

import java.util.Arrays;
import java.util.List;

/**
 * Created by ehallmark on 3/10/17.
 */
public class AIValueModel extends RegressionValueModel {
    private static final double INTERCEPT = -7.899e-1;
    private static final List<Double> WEIGHTS = Arrays.asList(
            1.377e-2,
            -8.002e-2,
            7.559e-2,
            -5.715e-3,
            4.852e5
    );

    public static final List<AbstractAttribute> MODELS = Arrays.asList(
            new CountAttribute(Constants.CLAIMS+Constants.COUNT_SUFFIX),
            new NumAssignmentsAttribute(),
            new CountAttribute(Constants.PATENT_FAMILY+Constants.COUNT_SUFFIX),
            new LengthOfSmallestIndependentClaimAttribute(),
            new PageRankEvaluator()
    );

    public AIValueModel() {
        super(INTERCEPT, MODELS, WEIGHTS);
    }

    @Override
    public String getName() {
        return "aiValueRegressionModel";
    }

}
