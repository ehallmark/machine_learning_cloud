package analysis.genetics.keyword_analysis;

import analysis.genetics.GeneticAlgorithm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 2/19/2017.
 */
public class TechnologyPredictionAlgorithm {

    public static void main(String[] args) {
        int populationSize = 500;
        double samplingProbability = 0.01;
        int numThreads = Runtime.getRuntime().availableProcessors()*4;
        int numEpochs = 50000;
        double mutationRate = 0.5;
        int minWordsPerTech = 50;
        double crossoverRate = 0.5;
        Map<String,List<Word>> allWordsMap = KeywordSolution.getAllWordsMap();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(new KeywordSolutionCreator(allWordsMap, samplingProbability,minWordsPerTech,numThreads),populationSize, new KeywordListener(),numThreads);
        algorithm.simulate(numEpochs,mutationRate,crossoverRate);
    }
}
