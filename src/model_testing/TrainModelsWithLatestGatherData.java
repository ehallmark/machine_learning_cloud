package model_testing;

import ui_models.attributes.classification.*;
import seeding.Database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
        //models.add(SimilarityGatherTechTagger.getAIModelTagger());
        models.add(NaiveGatherClassifier.get());
        //models.add(GatherSVMClassifier.get());


        int numEpochs = 50;

        GatherClassificationOptimizer optimizer = new GatherClassificationOptimizer(rawTrainingData,rawValidation1Data,rawValidation2Data);
        double bestAccuracy = 0d;
        for(int i = 0; i < numEpochs; i++) {
            System.out.println("Starting epoch: "+i);
            long t1 = System.currentTimeMillis();
            // Optimize Structure (i.e gatherTechToBroadTechMap)
            gatherTechToBroadTechMap = optimizer.optimizeBroadTechnologies(models,gatherTechToBroadTechMap);

            // Optimize HyperParameters (i.e alpha of Dirichlet dist.)
            ClassificationAttr optimizedCombinedModel = optimizer.optimizeHyperParameters(models,gatherTechToBroadTechMap);
            // Test
            Map<String,Collection<String>> newGroupedTestData = SplitModelData.regroupData(rawTestingData,gatherTechToBroadTechMap);
            System.out.println("Testing data size: "+newGroupedTestData.size());
            System.out.println("Testing Models:");
            optimizer.testModel(models,newGroupedTestData);
            System.out.println("Testing Combined Model: ");
            double accuracy = optimizer.testModel(Arrays.asList(optimizedCombinedModel),newGroupedTestData);
            long t2 = System.currentTimeMillis();
            System.out.println("Time to complete: "+ new Double(t2-t1)/(1000 * 60) + " minutes");
            System.out.println("Current accuracy: "+accuracy);
            System.out.println("Best accuracy: "+bestAccuracy);
            if(accuracy>bestAccuracy) {
                bestAccuracy=accuracy;
                System.out.println("Found better model! Saving results now...");
                for(ClassificationAttr model : models) {
                    model.save();
                }
                SplitModelData.saveBroadTechMap(gatherTechToBroadTechMap);
                writeToCSV(gatherTechToBroadTechMap,new File("data/ai_grouped_gather_technologies.csv"));
            }
        }
    }



    private static void writeToCSV(Map<String,String> techMap,File file) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));

            techMap.entrySet().stream().sorted((e1,e2)->e1.getValue().compareTo(e2.getValue())).forEach(e->{
                String broad = e.getValue();
                String specific = e.getKey();
                try {
                    writer.write("\"" + broad + "\",\"" + specific + "\"\n");
                } catch(Exception ex) {
                    ex.printStackTrace();
                }
            });

            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
