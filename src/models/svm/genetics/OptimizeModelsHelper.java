package models.svm.genetics;

import models.genetics.GeneticAlgorithm;
import models.genetics.Listener;
import models.genetics.SolutionCreator;
import models.model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import models.svm.SVMHelper;
import models.classification_models.GatherSVMClassifier;

import java.util.Collection;
import java.util.Map;

/**
 * Created by Evan on 5/24/2017.
 */
public class OptimizeModelsHelper {
    public static void optimizeModel(GatherSVMClassifier classifier) {
        double probMutation = 0.5;
        double probCrossover = 0.5;
        int populationSize = 100;
        int numThreads = 20;
        long timeLimit = 120 * 60 * 1000;

        // get data
        Map<String,Collection<String>> gatherTrainingMap = SplitModelData.getBroadDataMap(SplitModelData.trainFile);
        Map<String,Collection<String>> gatherValidationMap = SplitModelData.getBroadDataMap(SplitModelData.validation1File);

        System.out.println("Building models.svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToSVMData(gatherTrainingMap,classifier.getOrderedTechnologies(),classifier.getLookupTable());

        System.out.println("Starting genetic algorithm...");
        SolutionCreator creator = new SVMSolutionCreator(training,gatherValidationMap,classifier.getOrderedTechnologies(), classifier.getLookupTable());
        Listener listener = new SVMSolutionListener();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(creator,populationSize,listener,numThreads);
        algorithm.simulate(timeLimit,probMutation,probCrossover);
    }
}
