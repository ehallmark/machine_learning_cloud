package analysis.genetics.keyword_analysis;

import analysis.genetics.GeneticAlgorithm;
import org.deeplearning4j.berkeley.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 2/19/2017.
 */
public class TechnologyPredictionAlgorithm {

    public static void main(String[] args) {
        int populationSize = 1000;
        int numThreads = Runtime.getRuntime().availableProcessors()*2;
        int numEpochs = 10*60*1000;
        double mutationRate = 0.7;
        double crossoverRate = 0.8;
        Map<String,List<Pair<String,Double>>> allWordsMap = KeywordSolution.getAllWordsMap();
        GeneticAlgorithm algorithm = new GeneticAlgorithm(new KeywordSolutionCreator(allWordsMap,numThreads),populationSize, new KeywordListener(),numThreads);
        algorithm.simulate(numEpochs,mutationRate,crossoverRate);
    }
}
