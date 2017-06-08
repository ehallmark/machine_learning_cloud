package svm.genetics;

import genetics.GeneticAlgorithm;
import genetics.Listener;
import genetics.SolutionCreator;
import graphical_models.classification.CPCKMeans;
import model_testing.SplitModelData;
import org.deeplearning4j.berkeley.Pair;
import svm.SVMHelper;
import ui_models.attributes.classification.ClassificationSVMClassifier;
import ui_models.attributes.classification.SimilarityGatherTechTagger;

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
        Map<String,Collection<String>> gatherTrainingMap = SplitModelData.getBroadDataMap(SplitModelData.trainFile);
        Map<String,Collection<String>> gatherValidationMap = SplitModelData.getBroadDataMap(SplitModelData.validation1File);
        ClassificationSVMClassifier classifier = ClassificationSVMClassifier.get();
        int cpcDepth = CPCKMeans.DEFAULT_CPC_DEPTH;
        System.out.println("Building svm data...");
        Pair<double[][],double[][]> training = SVMHelper.mapToCPCSVMData(gatherTrainingMap,classifier.getOrderedTechnologies(),classifier.getOrderedClassifications(),cpcDepth);

        System.out.println("Starting genetic algorithm...");
        SolutionCreator creator = new CPCSVMSolutionCreator(training,gatherValidationMap,classifier.getOrderedTechnologies(),classifier.getOrderedClassifications(),cpcDepth);
        Listener listener = new SVMSolutionListener();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(creator,populationSize,listener,numThreads);
        algorithm.simulate(timeLimit,probMutation,probCrossover);
    }
}
