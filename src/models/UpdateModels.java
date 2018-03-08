package models;

import models.classification_models.UpdateClassificationModels;
import models.value_models.UpdateValueModels;
import seeding.Database;
import seeding.ai_db_updater.UpdateCompDBAndGatherData;
import user_interface.ui_models.attributes.computable_attributes.OverallEvaluator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateModels {
    public static void main(String[] args) throws Exception {
        runModels();
    }

    public static void runModels() throws Exception{
        UpdateValueModels.updateLatest(null);
    }
}
