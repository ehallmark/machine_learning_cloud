package analysis.genetics.keyword_analysis;

import analysis.genetics.GeneticAlgorithm;

import java.util.Map;

/**
 * Created by Evan on 2/19/2017.
 */
public class TechnologyPredictionAlgorithm {

    public static void main(String[] args) {
        int populationSize = 100;
        double samplingProbability = 0.01;
        int numEpochs = 10000;
        double mutationRate = 0.3;
        double crossoverRate = 0.02;
        Map<String,Map<String,Double>> techFrequencyMap = KeywordSolution.getTechnologyToWordFrequencyMap();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(new KeywordSolutionCreator(techFrequencyMap, samplingProbability),populationSize, new KeywordListener());
        algorithm.simulate(numEpochs,mutationRate,crossoverRate);
    }
}
