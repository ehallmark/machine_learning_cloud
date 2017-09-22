package user_interface.ui_models.attributes.computable_attributes;

import models.value_models.ValueModelCombination;
import models.value_models.graphical.WIPOValueModel;
import models.value_models.regression.AIValueModel;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 3/10/17.
 */
public class OverallEvaluator extends ValueModelCombination {

    public OverallEvaluator(boolean train) {
        super(Constants.AI_VALUE, new AIValueModel(),new WIPOValueModel(), train);
    }

}
