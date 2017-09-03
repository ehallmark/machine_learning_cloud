package models.value_models.regression;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 3/10/17.
 */
public class OverallEvaluator extends RegressionValueModel {
    private static final double INTERCEPT = 0;
    private static final List<Double> WEIGHTS = null;
    private static final Number DEFAULT_VAL = 0;
    private static final List<AbstractAttribute> MODELS = Arrays.asList(
            new CitationEvaluator(),
            new ClaimEvaluator(),
            new PageRankEvaluator(),
            new MeansPresentEvaluator());

    public OverallEvaluator() {
        super(INTERCEPT, MODELS, WEIGHTS, DEFAULT_VAL);
    }

    @Override
    public String getName() {
        return Constants.AI_VALUE;
    }

}
