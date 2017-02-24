package analysis.genetics.keyword_analysis;

import analysis.genetics.Solution;
import analysis.genetics.SolutionCreator;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/19/2017.
 */
public class KeywordSolutionCreator implements SolutionCreator {
    private Map<String,List<String>> techToWordMap;
    private static Random random = new Random(69);
    private final double samplingProbability;
    private final int minWordsPerTechnology;
    public KeywordSolutionCreator(Map<String,Map<String,Double>> techFrequencyMap, double samplingProbability, int minWordsPerTechnology) {
        this.techToWordMap=new HashMap<>();
        this.minWordsPerTechnology=minWordsPerTechnology;
        this.samplingProbability=samplingProbability;
        techFrequencyMap.forEach((tech,map)->{
            techToWordMap.put(tech,new ArrayList<>(map.keySet()));
        });
    }
    @Override
    public Solution nextRandomSolution() {
        Map<String,Set<String>> randomTechToWordMap = new HashMap<>();
        System.out.println("Creating random solution...");
        AtomicInteger size = new AtomicInteger(0);
        techToWordMap.forEach((tech,words)->{
            if(words.size()<minWordsPerTechnology*10) return; // avoid too small of samples
            int samples = Math.max(minWordsPerTechnology,Math.round((float)samplingProbability*words.size()));
            Set<String> newSet = new HashSet<>(samples);
            for(int i = 0; i < samples; i++) {
                String randomWord = words.get(random.nextInt(words.size()));
                newSet.add(randomWord);
            }
            size.getAndAdd(newSet.size());
            randomTechToWordMap.put(tech,newSet);
        });
        Solution solution = new KeywordSolution(randomTechToWordMap,minWordsPerTechnology);
        solution.calculateFitness();
        System.out.println("Solution Size: "+size.get());
        System.out.println("Solution score: "+solution.fitness());
        return solution;
    }
}
