package analysis.genetics.keyword_analysis;

import analysis.genetics.GeneticAlgorithm;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by Evan on 2/19/2017.
 */
public class TechnologyPredictionAlgorithm {

    public static void main(String[] args) {
        int populationSize = 500;
        double samplingProbability = 0.01;
        int numThreads = 30;
        int numEpochs = 50000;
        double mutationRate = 0.1;
        int minWordsPerTech = 50;
        double crossoverRate = 0.1;
        Map<String,Map<String,Double>> techFrequencyMap = KeywordSolution.getTechnologyToWordFrequencyMap();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(new KeywordSolutionCreator(techFrequencyMap, samplingProbability,minWordsPerTech),populationSize, new KeywordListener(),numThreads);
        algorithm.simulate(numEpochs,mutationRate,crossoverRate);
    }
}
