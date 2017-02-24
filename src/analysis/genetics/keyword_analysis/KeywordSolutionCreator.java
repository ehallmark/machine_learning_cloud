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
    public Collection<Solution> nextRandomSolutions(int num) {
        List<Map<String,List<Word>>> randomTechToWordMapList = new ArrayList<>(num);
        List<Map<String,Set<String>>> alreadyAddedMapList = new ArrayList<>(num);
        for(int i = 0; i < num; i++) {
            randomTechToWordMapList.add(new HashMap<>(techToWordMap.size()));
            alreadyAddedMapList.add(new HashMap<>(techToWordMap.size()));
        }
        System.out.println("Creating random solution...");
        AtomicInteger size = new AtomicInteger(0);
        techToWordMap.forEach((tech,words)->{
            for(int solutionNum = 0; solutionNum < num; solutionNum++) {
                Map<String,List<Word>> randomTechToWordMap = randomTechToWordMapList.get(solutionNum);
                Map<String,Set<String>> alreadyAddedMap = alreadyAddedMapList.get(solutionNum);
                int samples = Math.max(minWordsPerTechnology, Math.round((float) samplingProbability * words.size()));
                List<Word> newSet = new ArrayList<>(samples);
                Set<String> wordSet = new HashSet<>(samples);
                for (int i = 0; i < samples; i++) {
                    int randIdx = ProbabilityHelper.getLowNumberWithMaxUpTo(words.size());
                    Word randomWord = words.get(randIdx);
                    if (wordSet.contains(randomWord.getWord())) {
                        boolean ended = false;
                        randIdx--;
                        while(randIdx>=0) {
                            randomWord=words.get(randIdx);
                            if(!wordSet.contains(randomWord.getWord())) {
                                wordSet.add(randomWord.getWord());
                                newSet.add(randomWord);
                                ended=true;
                            }
                            randIdx--;
                        }
                        if(!ended) {
                            i--;
                        }
                    } else {
                        newSet.add(randomWord);
                        wordSet.add(randomWord.getWord());
                    }
                }
                size.getAndAdd(newSet.size());
                randomTechToWordMap.put(tech, newSet);
                alreadyAddedMap.put(tech, wordSet);
            }
        });
        Collection<Solution> solutions = new HashSet<>(num);
        for(int i = 0; i < num; i++) {
            Solution solution = new KeywordSolution(randomTechToWordMapList.get(i),alreadyAddedMapList.get(i),minWordsPerTechnology);
            solution.calculateFitness();
            solutions.add(solution);
            System.out.println("Solution score: "+solution.fitness());
        }
        return solutions;
    }
}
