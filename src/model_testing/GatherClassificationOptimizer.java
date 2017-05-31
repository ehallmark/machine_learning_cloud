package model_testing;

import genetics.GeneticAlgorithm;
import genetics.Listener;
import genetics.SolutionCreator;
import lombok.Getter;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.classification.TechTaggerNormalizer;
import ui_models.attributes.classification.broad_technology.BroadTechnologySolution;
import ui_models.attributes.classification.broad_technology.BroadTechnologySolutionCreator;
import ui_models.attributes.classification.broad_technology.BroadTechnologySolutionListener;
import ui_models.attributes.classification.genetics.TechTaggerSolution;
import ui_models.attributes.classification.genetics.TechTaggerSolutionCreator;
import ui_models.attributes.classification.genetics.TechTaggerSolutionListener;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 5/30/17.
 */
public class GatherClassificationOptimizer {
    private int populationSize = 30;
    private int numThreads = 20;
    private double probMutation = 0.5;
    private double probCrossover = 0.5;
    private long timeLimit = 10 * 60 * 1000;
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
        BroadTechnologySolutionListener listener = null;// new BroadTechnologySolutionListener();
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
            models.get(i).train(broadTrainingData);
        }

        // Genetic Algorithm to find better combination
        System.out.println("Starting genetic algorithm...");
        SolutionCreator<TechTaggerSolution> creator = new TechTaggerSolutionCreator(broadValidation2Data,models);
        Listener listener = null;//new TechTaggerSolutionListener();
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

}
