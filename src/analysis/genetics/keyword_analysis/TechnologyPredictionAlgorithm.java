package analysis.genetics.keyword_analysis;

import analysis.genetics.GeneticAlgorithm;

import java.util.Map;

/**
 * Created by Evan on 2/19/2017.
 */
public class TechnologyPredictionAlgorithm {

    public static void main(String[] args) {
        int populationSize = 10;
        double samplingProbability = 0.00001;
        int numEpochs = 10;
        double mutationRate = 0.0005;
        double crossoverRate = 0.001;
        Map<String,Map<String,Double>> techFrequencyMap = KeywordSolution.getTechnologyToWordFrequencyMap();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(new KeywordSolutionCreator(techFrequencyMap, samplingProbability),populationSize);
        algorithm.simulate(numEpochs,mutationRate,crossoverRate);
    }
}
