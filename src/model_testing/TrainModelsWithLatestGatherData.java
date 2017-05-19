package model_testing;

import graphical_models.classification.NaiveGatherClassifier;
import ui_models.attributes.classification.CPCGatherTechTagger;
import ui_models.attributes.classification.KeywordGatherTechTagger;
import seeding.Database;

import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 5/4/17.
 */
public class TrainModelsWithLatestGatherData {
    public static void main(String[] args) throws Exception{
        Database.initializeDatabase();

        Map<String,Collection<String>> trainingData = SplitModelData.getGatherTechnologyTrainingDataMap();
        Map<String,Collection<String>> testingData = SplitModelData.getGatherTechnologyTestDataMap();
        Map<String,Collection<String>> validationData = SplitModelData.getGatherTechnologyValidationDataMap();

        // train gather keyword model
        KeywordGatherTechTagger.trainAndSaveLatestModel(trainingData,testingData,validationData);


        // train gather cpc model
        CPCGatherTechTagger.trainAndSaveLatestModel(trainingData,testingData,validationData);


        // train naive bayesian model
        NaiveGatherClassifier.main(new String[]{});

    }
}
