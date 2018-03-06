package models.classification_models;

import models.keyphrase_prediction.PredictKeyphraseForFilings;

/**
 * Created by ehallmark on 7/11/17.
 */
public class UpdateClassificationModels {
    public static void main(String[] args) throws Exception {
        updateLatest();
    }

    // TODO
    public static void updateLatest() {
        PredictKeyphraseForFilings.runPredictions(false);
    }
}
