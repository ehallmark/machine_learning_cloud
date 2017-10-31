package models.classification_models;

import models.keyphrase_prediction.KeywordModelRunner;

import java.util.Collection;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateClassificationModels {
    public static void main(String[] args) throws Exception {
        updateLatest(false);
    }

    public static void updateLatest(boolean newAssetsOnly) {
        KeywordModelRunner.runModel(newAssetsOnly);
    }
}
