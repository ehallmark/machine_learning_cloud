package model_testing;

import ui_models.attributes.classification.*;
import seeding.Database;

import java.util.*;

/**
 * Created by ehallmark on 5/4/17.
 */
public class TrainModelsWithLatestGatherData {
    public static void main(String[] args) throws Exception{
        Database.initializeDatabase();

        Map<String,Collection<String>> rawTrainingData = SplitModelData.getRawDataMap(SplitModelData.trainFile);
        Map<String,Collection<String>> rawTestingData = SplitModelData.getRawDataMap(SplitModelData.testFile);
        Map<String,Collection<String>> rawValidation1Data = SplitModelData.getRawDataMap(SplitModelData.validation1File);
        Map<String,Collection<String>> rawValidation2Data = SplitModelData.getRawDataMap(SplitModelData.validation2File);

        Map<String,String> gatherTechToBroadTechMap = new HashMap<>(SplitModelData.gatherToBroadTechMap);


        List<ClassificationAttr> models = new ArrayList<>();
        // Add classification models
        models.add(CPCGatherTechTagger.get());
        models.add(SimilarityGatherTechTagger.getAIModelTagger());
        models.add(NaiveGatherClassifier.get());
        models.add(GatherSVMClassifier.get());


        int numEpochs = 30;

        GatherClassificationOptimizer optimizer = new GatherClassificationOptimizer();
        for(int i = 0; i < numEpochs; i++) {
            // Train on data
            optimizer.trainModels(models,SplitModelData.regroupData(rawTrainingData,gatherTechToBroadTechMap));
            // Optimize Structure
            gatherTechToBroadTechMap = optimizer.optimizeBroadTechnologies(models,rawValidation1Data,gatherTechToBroadTechMap);
            // Retrain on Data
            optimizer.trainModels(models,SplitModelData.regroupData(rawTrainingData,gatherTechToBroadTechMap));
            // Optimize HyperParameters
            ClassificationAttr optimizedCombinedModel = optimizer.optimizeHyperParameters(models,SplitModelData.regroupData(rawTestingData,gatherTechToBroadTechMap),SplitModelData.regroupData(rawValidation2Data,gatherTechToBroadTechMap));
            // Test
            Map<String,Collection<String>> newGroupedTestData = SplitModelData.regroupData(rawTestingData,gatherTechToBroadTechMap);
            System.out.println("Testing Models:");
            optimizer.testModel(models,newGroupedTestData);
            System.out.println("Testing Combined Mdoel: ");
            optimizer.testModel(Arrays.asList(optimizedCombinedModel),newGroupedTestData);
        }
    }



}
