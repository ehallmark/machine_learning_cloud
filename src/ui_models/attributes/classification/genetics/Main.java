package ui_models.attributes.classification.genetics;

import genetics.GeneticAlgorithm;
import genetics.Listener;
import genetics.SolutionCreator;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import svm.SVMHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 5/24/2017.
 */
public class Main {
    public static void main(String[] args) {
        double probMutation = 0.5;
        double probCrossover = 0.5;
        int populationSize = 100;
        int numThreads = 20;
        long timeLimit = 120 * 60 * 1000;

        // get data
        Map<String,Collection<String>> gatherValidationMap = SplitModelData.getGatherTechnologyValidationDataMap();

        System.out.println("Starting genetic algorithm...");
        SolutionCreator creator = new TechTaggerSolutionCreator(gatherValidationMap);
        Listener listener = new TechTaggerSolutionListener();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(creator,populationSize,listener,numThreads);
        algorithm.simulate(timeLimit,probMutation,probCrossover);
    }
}
