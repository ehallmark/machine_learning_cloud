package analysis.genetics.keyword_analysis;

import analysis.genetics.Solution;
import analysis.genetics.SolutionCreator;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
        for(int i = 0; i < num; i++) {
            randomTechToWordMapList.add(Collections.synchronizedMap(new HashMap<>(techToWordMap.size())));
        }
        AtomicInteger cnt = new AtomicInteger(1);
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        techToWordMap.forEach((tech,words)->{
            RecursiveAction action = new RecursiveAction() {
                @Override
                protected void compute() {
                    System.out.println("Creating random "+tech+" solution ["+cnt.getAndIncrement()+"/"+techToWordMap.size()+"]");
                    for(int solutionNum = 0; solutionNum < num; solutionNum++) {
                        Map<String,List<Word>> randomTechToWordMap = randomTechToWordMapList.get(solutionNum);
                        SortedSet<Word> newSet = new TreeSet<>();
                        while(newSet.size()<wordsPerTech) {
                            int randIdx = random.nextInt(words.size());
                            Word randomWord = words.get(randIdx);
                            newSet.add(randomWord);
                        }
                        List<Word> newList = new ArrayList<>(newSet);
                        if(newList.size()==newSet.size()) {
                            randomTechToWordMap.put(tech, newList);
                        } else {
                            System.out.println("Wordset is not the same size as the new set");
                            throw new RuntimeException("Invalid solution in solution creator");
                        }
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
            KeywordSolution solution = new KeywordSolution(randomTechToWordMapList.get(i),wordsPerTech);
            if((!validateSolution(solution,wordsPerTech))) {
                System.out.println("Invalid solution!");
                continue;
            }
            solution.calculateFitness();
            solutions.add(solution);
            System.out.println("Solution score: "+solution.fitness());
        }
        return solutions;
    }

    static boolean validateSolution(KeywordSolution solution, int wordsPerTech) {
        AtomicBoolean val = new AtomicBoolean(true);
        solution.getTechnologyToWordsMap().forEach((tech,words)->{
            if(tech==null||words==null||words.size()!=wordsPerTech) {
                val.set(false);
                //System.out.println("INVALID TECHNOLOGY: "+tech);
            } else if (words.size()!=new TreeSet<>(words).size()) {
                val.set(false);
            }
        });
        return val.get();
    }
}
