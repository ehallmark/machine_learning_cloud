package analysis.genetics.keyword_analysis;

import analysis.genetics.Solution;
import analysis.genetics.SolutionCreator;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/19/2017.
 */
public class KeywordSolutionCreator implements SolutionCreator {
    private Map<String,List<Word>> techToWordMap;
    private final double samplingProbability;
    private final int minWordsPerTechnology;
    public KeywordSolutionCreator(Map<String,List<Word>> allWordMap, double samplingProbability, int minWordsPerTechnology) {
        this.techToWordMap=new HashMap<>();
        this.minWordsPerTechnology=minWordsPerTechnology;
        this.samplingProbability=samplingProbability;
        techToWordMap=allWordMap;
    }
    @Override
    public Solution nextRandomSolution() {
        Map<String,List<Word>> randomTechToWordMap = new HashMap<>();
        Map<String,Set<String>> alreadyAddedMap = new HashMap<>();
        System.out.println("Creating random solution...");
        AtomicInteger size = new AtomicInteger(0);
        techToWordMap.forEach((tech,words)->{
            int samples = Math.max(minWordsPerTechnology,Math.round((float)samplingProbability*words.size()));
            List<Word> newSet = new ArrayList<>(samples);
            Set<String> wordSet = new HashSet<>(samples);
            for(int i = 0; i < samples; i++) {
                Word randomWord = words.get(ProbabilityHelper.getLowNumberWithMaxUpTo(words.size()));
                if(wordSet.contains(randomWord.getWord())) {
                    i--;
                } else {
                    newSet.add(randomWord);
                    wordSet.add(randomWord.getWord());
                }
                System.out.println("Words found so far: "+newSet.size());
            }
            size.getAndAdd(newSet.size());
            randomTechToWordMap.put(tech,newSet);
            alreadyAddedMap.put(tech,wordSet);
        });
        Solution solution = new KeywordSolution(randomTechToWordMap,alreadyAddedMap,minWordsPerTechnology);
        solution.calculateFitness();
        System.out.println("Solution Size: "+size.get());
        System.out.println("Solution score: "+solution.fitness());
        return solution;
    }
}
