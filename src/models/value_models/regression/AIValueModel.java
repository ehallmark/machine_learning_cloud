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
            -5.103e-01,//-9.028e-1,
            1.562e-02,//1.377e-2,
            3.670e-02,//-8.002e-2,
            3.952e-02,//7.559e-2,
            -6.050e-03,//-5.715e-3,
            9.151e+05//4.852e5
    );

    public static final List<AbstractAttribute> MODELS = Arrays.asList(
            new MeansPresentAttribute(),
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
