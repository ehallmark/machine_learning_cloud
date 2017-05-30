package model_testing;

import genetics.GeneticAlgorithm;
import genetics.Listener;
import genetics.SolutionCreator;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.classification.TechTaggerNormalizer;
import ui_models.attributes.classification.broad_technology.BroadTechnologySolution;
import ui_models.attributes.classification.broad_technology.BroadTechnologySolutionCreator;
import ui_models.attributes.classification.genetics.TechTaggerSolution;
import ui_models.attributes.classification.genetics.TechTaggerSolutionCreator;
import ui_models.attributes.classification.genetics.TechTaggerSolutionListener;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 5/30/17.
 */
public class GatherClassificationOptimizer {
    private int populationSize = 30;
    private int numThreads = 20;
    private double probMutation = 0.5;
    private double probCrossover = 0.5;
    private long timeLimit = 10 * 60 * 1000;

    // Trains model one at a time
    public void trainModels(List<ClassificationAttr> models, Map<String,Collection<String>> broadTrainingData) {
        models.forEach(model->{
            model.train(broadTrainingData);
        });
    }

    // Returns better mapping of specific gather technologies to broad technologies
    public Map<String,String> optimizeBroadTechnologies(List<ClassificationAttr> models, Map<String,Collection<String>> rawValidationData1, Map<String,String> broadTechMap) {
        Map<String,String> betterBroadTechMap = new HashMap<>(broadTechMap);
        // Genetic Algorithm to find better broad technologies
        System.out.println("Starting genetic algorithm...");
        SolutionCreator<BroadTechnologySolution> creator = new BroadTechnologySolutionCreator(broadTechMap,models,rawValidationData1);
        Listener listener = new TechTaggerSolutionListener();
        GeneticAlgorithm<BroadTechnologySolution> algorithm = new GeneticAlgorithm<>(creator,populationSize,listener,numThreads);
        algorithm.simulate(timeLimit,probMutation,probCrossover);
        System.out.println("Finished optimizing hyper parameters.");
        return betterBroadTechMap;
    }

    public ClassificationAttr optimizeHyperParameters(List<ClassificationAttr> models, Map<String,Collection<String>> broadTrainingData, Map<String,Collection<String>> broadValidationData2) {
        // Optimize parameters for each model
        models.forEach(model->{
            model.optimizeHyperParameters(broadTrainingData,broadValidationData2);
        });

        // Genetic Algorithm to find better combination
        System.out.println("Starting genetic algorithm...");
        SolutionCreator<TechTaggerSolution> creator = new TechTaggerSolutionCreator(broadValidationData2,models);
        Listener listener = new TechTaggerSolutionListener();
        GeneticAlgorithm<TechTaggerSolution> algorithm = new GeneticAlgorithm<>(creator,populationSize,listener,numThreads);
        algorithm.simulate(timeLimit,probMutation,probCrossover);
        System.out.println("Finished optimizing hyper parameters.");
        return new TechTaggerNormalizer(algorithm.getBestSolution().getTaggers(),algorithm.getBestSolution().getWeights());
    }

    public void testModel(List<ClassificationAttr> models, Map<String,Collection<String>> broadTestData) {
        models.forEach(model->{
            GatherTechnologyScorer scorer = new GatherTechnologyScorer(model);
            for (int i = 1; i <= 5; i += 2) {
                ModelTesterMain.testModel(model.getClass().getName()+" [n=" + i + "]", scorer, broadTestData, i);
            }
        });
    }

}
