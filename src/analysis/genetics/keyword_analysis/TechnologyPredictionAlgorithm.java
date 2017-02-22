package analysis.genetics.keyword_analysis;

import analysis.genetics.GeneticAlgorithm;

import java.util.Map;

/**
 * Created by Evan on 2/19/2017.
 */
public class TechnologyPredictionAlgorithm {

    public static void main(String[] args) {
        int populationSize = 10;
        double samplingProbability = 0.05;
        int numEpochs = 10000;
        double mutationRate = 0.01;
        double crossoverRate = 0.1;
        Map<String,Map<String,Double>> techFrequencyMap = KeywordSolution.getTechnologyToWordFrequencyMap();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(new KeywordSolutionCreator(techFrequencyMap, samplingProbability),populationSize);
        algorithm.simulate(numEpochs,mutationRate,crossoverRate);
    }
}
