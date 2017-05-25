package svm.genetics;

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
        double probMutation = 0.7;
        double probCrossover = 0.7;
        int populationSize = 50;
        int numThreads = 40;
        long timeLimit = 120 * 60 * 1000; 

        // get data
        Map<String,Collection<String>> gatherTrainingMap = SplitModelData.getGatherTechnologyTrainingDataMap();
        List<String> orderedTechnologies = new ArrayList<>(gatherTrainingMap.keySet());
        Map<String,Collection<String>> gatherValidationMap = SplitModelData.getGatherTechnologyValidationDataMap();

        System.out.println("Building svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(gatherTrainingMap,orderedTechnologies);

        System.out.println("Starting genetic algorithm...");
        SolutionCreator creator = new SVMSolutionCreator(training,gatherValidationMap,orderedTechnologies);
        Listener listener = new SVMSolutionListener();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(creator,populationSize,listener,numThreads);
        algorithm.simulate(timeLimit,probMutation,probCrossover);
    }
}
