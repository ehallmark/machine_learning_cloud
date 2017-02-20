package analysis.genetics.keyword_analysis;

import analysis.genetics.Solution;
import analysis.genetics.SolutionCreator;

import java.util.*;

/**
 * Created by Evan on 2/19/2017.
 */
public class KeywordSolutionCreator implements SolutionCreator {
    private Map<String,Set<String>> techToWordMap;
    private static Random random = new Random(69);
    private final double samplingProbability;
    public KeywordSolutionCreator(Map<String,Map<String,Double>> techFrequencyMap, double samplingProbability) {
        this.techToWordMap=new HashMap<>();
        this.samplingProbability=samplingProbability;
        techFrequencyMap.forEach((tech,map)->{
            techToWordMap.put(tech,new HashSet<>(map.keySet()));
        });
    }
    @Override
    public Solution nextRandomSolution() {
        Map<String,Set<String>> randomTechToWordMap = new HashMap<>();
        System.out.println("Creating random solution...");
        techToWordMap.forEach((tech,words)->{
            Set<String> newSet = new HashSet<>((int)(samplingProbability*words.size()));
            words.forEach(word->{
                if(random.nextDouble()<samplingProbability) {
                    newSet.add(word);
                }
            });
            randomTechToWordMap.put(tech,newSet);
        });
        return new KeywordSolution(randomTechToWordMap);
    }
}
