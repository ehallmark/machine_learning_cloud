package models.value_models;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.ValueAttr;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class MeansPresentEvaluator extends ValueAttr {
    public MeansPresentEvaluator() {
        super(ValueMapNormalizer.DistributionType.Uniform);
    }

    @Override
    public String getName() {
        return Constants.MEANS_PRESENT;
    }
}
