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
        int numThreads = Runtime.getRuntime().availableProcessors()*2;
        int numEpochs = 50000;
        double mutationRate = 0.5;
        int minWordsPerTech = 250;
        double crossoverRate = 0.5;
        Map<String,List<Word>> allWordsMap = KeywordSolution.getAllWordsMap();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(new KeywordSolutionCreator(allWordsMap,minWordsPerTech,numThreads),populationSize, new KeywordListener(),numThreads);
        algorithm.simulate(numEpochs,mutationRate,crossoverRate);
    }
}
