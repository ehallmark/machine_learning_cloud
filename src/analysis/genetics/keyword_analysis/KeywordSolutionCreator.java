package analysis.genetics.keyword_analysis;

import analysis.genetics.Solution;
import analysis.genetics.SolutionCreator;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/19/2017.
 */
public class KeywordSolutionCreator implements SolutionCreator {
    private Map<String,List<Word>> techToWordMap;
    private final int numThreads;
    private static final Random random = new Random(69);
    public KeywordSolutionCreator(Map<String,List<Word>> allWordMap, int numThreads) {
        this.techToWordMap=allWordMap;
        this.numThreads=numThreads;
    }
    @Override
    public Collection<Solution> nextRandomSolutions(int num) {
        final int wordsPerTech = KeywordSolution.WORDS_PER_TECH;
        List<Map<String,List<Word>>> randomTechToWordMapList = Collections.synchronizedList(new ArrayList<>(num));
        List<Map<String,Set<String>>> alreadyAddedMapList = Collections.synchronizedList(new ArrayList<>(num));
        for(int i = 0; i < num; i++) {
            randomTechToWordMapList.add(new HashMap<>(techToWordMap.size()));
            alreadyAddedMapList.add(new HashMap<>(techToWordMap.size()));
        }
        AtomicInteger cnt = new AtomicInteger(1);
        AtomicInteger size = new AtomicInteger(0);
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        techToWordMap.forEach((tech,words)->{
            RecursiveAction action = new RecursiveAction() {
                @Override
                protected void compute() {
                    System.out.println("Creating random "+tech+" solution ["+cnt.getAndIncrement()+"/"+techToWordMap.size()+"]");
                    for(int solutionNum = 0; solutionNum < num; solutionNum++) {
                        Map<String,List<Word>> randomTechToWordMap = randomTechToWordMapList.get(solutionNum);
                        Map<String,Set<String>> alreadyAddedMap = alreadyAddedMapList.get(solutionNum);
                        List<Word> newSet = new ArrayList<>(wordsPerTech);
                        Set<String> wordSet = new HashSet<>(wordsPerTech);
                        for (int i = 0; i < wordsPerTech; i++) {
                            int randIdx = random.nextInt(words.size());
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
                                        break;
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
                    System.out.println("Finished "+tech+" solution ["+cnt.get()+"/"+techToWordMap.size()+"]");
                }
            };
            pool.execute(action);
        });
        pool.shutdown();
        try {
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MICROSECONDS);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        Collection<Solution> solutions = new HashSet<>(num);
        for(int i = 0; i < num; i++) {
            Solution solution = new KeywordSolution(randomTechToWordMapList.get(i),alreadyAddedMapList.get(i),wordsPerTech);
            solution.calculateFitness();
            solutions.add(solution);
            System.out.println("Solution score: "+solution.fitness());
        }
        return solutions;
    }
}
