package model_testing;

import genetics.GeneticAlgorithm;
import genetics.Listener;
import genetics.SolutionCreator;
import lombok.Getter;
import seeding.Database;
import ui_models.attributes.classification.*;
import ui_models.attributes.classification.broad_technology.BroadTechnologySolution;
import ui_models.attributes.classification.broad_technology.BroadTechnologySolutionCreator;
import ui_models.attributes.classification.broad_technology.BroadTechnologySolutionListener;
import ui_models.attributes.classification.genetics.TechTaggerSolution;
import ui_models.attributes.classification.genetics.TechTaggerSolutionCreator;
import ui_models.attributes.classification.genetics.TechTaggerSolutionListener;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/30/17.
 */
public class GatherClassificationOptimizer {
    private int populationSize = 10;
    private int numThreads = 10;
    private double probMutation = 0.5;
    private double probCrossover = 0.5;
    private long timeLimit = 1 * 60 * 1000;
    @Getter
    private final Map<String,Collection<String>> rawTrainingData;
    @Getter
    private final Map<String,Collection<String>> rawValidation1Data;
    @Getter
    private final Map<String,Collection<String>> rawValidation2Data;

    public GatherClassificationOptimizer(Map<String,Collection<String>> rawTrainingData, Map<String,Collection<String>> rawValidation1Data, Map<String,Collection<String>> rawValidation2Data) {
        this.rawTrainingData=Collections.synchronizedMap(rawTrainingData);
        this.rawValidation1Data=Collections.synchronizedMap(rawValidation1Data);
        this.rawValidation2Data=Collections.synchronizedMap(rawValidation2Data);
    }

    // Trains model one at a time
    public List<ClassificationAttr> duplicateAndTrainModels(List<ClassificationAttr> models, Map<String,String> broadTechMap) {
        Map<String,Collection<String>> broadTrainingData = SplitModelData.regroupData(rawTrainingData,broadTechMap);
        List<ClassificationAttr> toReturn = new ArrayList<>(models.size());
        for(ClassificationAttr model : models) {
            ClassificationAttr newModel = model.untrainedDuplicate();
            newModel.train(broadTrainingData);
            toReturn.add(newModel);
        }
        return toReturn;
    }

    // Returns better mapping of specific gather technologies to broad technologies
    public Map<String,String> optimizeBroadTechnologies(List<ClassificationAttr> models, Map<String,String> broadTechMap) {
        // Genetic Algorithm to find better broad technologies
        System.out.println("Starting genetic algorithm...");
        SolutionCreator<BroadTechnologySolution> creator = new BroadTechnologySolutionCreator(broadTechMap,models,this);
        BroadTechnologySolutionListener listener = new BroadTechnologySolutionListener();
        GeneticAlgorithm<BroadTechnologySolution> algorithm = new GeneticAlgorithm<>(creator,populationSize,listener,numThreads);
        algorithm.simulate(timeLimit,probMutation,probCrossover);
        System.out.println("Finished optimizing broad technologies.");
        // Update models list from best solution and return better broad tech map
        BroadTechnologySolution bestSolution = algorithm.getBestSolution();
        models.clear();
        models.addAll(bestSolution.getModels());
        return bestSolution.getBroadTechMap();
    }

    public ClassificationAttr optimizeHyperParameters(List<ClassificationAttr> models, Map<String,String> broadTechMap) {
        // Get broad grouping
        Map<String,Collection<String>> broadTrainingData = SplitModelData.regroupData(rawTrainingData,broadTechMap);
        Map<String,Collection<String>> broadValidation2Data = SplitModelData.regroupData(rawValidation2Data,broadTechMap);

        // Optimize parameters for each model
        for(int i = 0; i < models.size(); i++) {
            models.set(i,models.get(i).optimizeHyperParameters(broadTrainingData,broadValidation2Data));
        }

        // Genetic Algorithm to find better combination
        System.out.println("Starting genetic algorithm...");
        SolutionCreator<TechTaggerSolution> creator = new TechTaggerSolutionCreator(broadValidation2Data,models);
        Listener listener = new TechTaggerSolutionListener();
        GeneticAlgorithm<TechTaggerSolution> algorithm = new GeneticAlgorithm<>(creator,populationSize,listener,numThreads);
        algorithm.simulate(timeLimit,probMutation,probCrossover);
        System.out.println("Finished optimizing hyper parameters.");
        return new TechTaggerNormalizer(algorithm.getBestSolution().getTaggers(),algorithm.getBestSolution().getWeights());
    }

    public double testModel(List<ClassificationAttr> models, Map<String,Collection<String>> broadTestData) {
        return models.stream().collect(Collectors.averagingDouble(model->{
            GatherTechnologyScorer scorer = new GatherTechnologyScorer(model);
            return scorer.accuracyOn(broadTestData, 3);
        }));
    }


    public static void main(String[] args) throws Exception{
        // SETUP
        Database.initializeDatabase();

        Map<String,Collection<String>> rawTrainingData = SplitModelData.getRawDataMap(SplitModelData.trainFile);
        Map<String,Collection<String>> rawTestingData = SplitModelData.getRawDataMap(SplitModelData.testFile);
        Map<String,Collection<String>> rawValidation1Data = SplitModelData.getRawDataMap(SplitModelData.validation1File);
        Map<String,Collection<String>> rawValidation2Data = SplitModelData.getRawDataMap(SplitModelData.validation2File);

        Map<String,String> gatherTechToBroadTechMap = new HashMap<>(SplitModelData.gatherToBroadTechMap);


        List<ClassificationAttr> models = new ArrayList<>();
        // Add classification models
        models.add(SimilarityGatherTechTagger.getParagraphVectorModel());
        models.add(SimilarityGatherTechTagger.getCPCModel());
        models.add(NaiveGatherClassifier.get());
        models.add(GatherSVMClassifier.getCPCModel());
        models.add(GatherSVMClassifier.getParagraphVectorModel());
       //Not yet Supported
        // models.add(TechTaggerNormalizer.getDefaultTechTagger());


        int numEpochs = 50;

        boolean optimizeModelStructure = false;

        GatherClassificationOptimizer optimizer = new GatherClassificationOptimizer(rawTrainingData,rawValidation1Data,rawValidation2Data);
        double bestAccuracy = 0d;
        for(int i = 0; i < numEpochs; i++) {
            System.out.println("Starting epoch: "+i);
            long t1 = System.currentTimeMillis();
            if(optimizeModelStructure) {
                System.out.println("Optimizing Model Structure");
                // Optimize Structure (i.e gatherTechToBroadTechMap)
                gatherTechToBroadTechMap = optimizer.optimizeBroadTechnologies(models,gatherTechToBroadTechMap);
            }

            // Optimize HyperParameters (i.e alpha of Dirichlet dist.)
            ClassificationAttr optimizedCombinedModel = optimizer.optimizeHyperParameters(models,gatherTechToBroadTechMap);
            // Test
            Map<String,Collection<String>> newGroupedTestData = SplitModelData.regroupData(rawTestingData,gatherTechToBroadTechMap);
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
                // for personally checking
                writeToCSV(gatherTechToBroadTechMap,new File("data/ai_grouped_gather_technologies.csv"));
            }
        }
    }



    public static void writeToCSV(Map<String,String> techMap,File file) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));

            techMap.entrySet().stream().sorted(Comparator.comparing(e->e.getValue())).forEach(e->{
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
